package com.glosh.remote.spike.adb;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Fixed-purpose, fail-closed Device Owner provisioning channel. */
public final class RemoteProvisioningController implements Closeable {
    private static final long MAX_APK_BYTES = 512L * 1024L * 1024L;
    private static final int MAX_CHUNK_BYTES = 128 * 1024;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final String RECEIPT_NAME = "device-owner-attempt-v1";

    private final Context context;
    private final AdbShell shell;
    private final File stagingDirectory;
    private Transfer active;

    public RemoteProvisioningController(Context context, AdbShell shell) {
        this.context = context.getApplicationContext();
        this.shell = shell.withContext(this.context);
        this.stagingDirectory = new File(this.context.getCacheDir(), "owner-provisioning");
    }

    public static boolean supports(String action) {
        return "maintenance-shell".equals(action)
                || "owner-preflight".equals(action)
                || "artifact-begin".equals(action)
                || "artifact-chunk".equals(action)
                || "artifact-stage".equals(action)
                || "owner-commit".equals(action);
    }

    public synchronized String execute(String action, JSONObject params) throws Exception {
        JSONObject safeParams = params == null ? new JSONObject() : params;
        switch (action) {
            case "maintenance-shell":
                return new JSONObject()
                        .put("output", shell.executeRemoteCommand(safeParams.getString("command")))
                        .toString();
            case "owner-preflight":
                return preflight().toString();
            case "artifact-begin":
                return begin(safeParams).toString();
            case "artifact-chunk":
                return append(safeParams).toString();
            case "artifact-stage":
                return stage(safeParams).toString();
            case "owner-commit":
                return commit(safeParams).toString();
            default:
                throw new SecurityException("Operación de aprovisionamiento no permitida.");
        }
    }

    private JSONObject preflight() throws Exception {
        ProvisioningPreflight.Snapshot snapshot = readPreflight();
        JSONArray types = new JSONArray();
        for (Map.Entry<String, Integer> entry : snapshot.accountTypes().entrySet()) {
            types.put(new JSONObject().put("type", entry.getKey()).put("count", entry.getValue()));
        }
        return new JSONObject()
                .put("eligible", snapshot.eligible())
                .put("ownerState", snapshot.ownerState().name())
                .put("userCount", snapshot.userCount())
                .put("hasPrimaryUser", snapshot.hasPrimaryUser())
                .put("accountCount", snapshot.accountCount())
                .put("accountTypes", types)
                .put("blockReason", snapshot.blockReason());
    }

    private ProvisioningPreflight.Snapshot readPreflight() throws Exception {
        return ProvisioningPreflight.parse(
                shell.runService("shell:pm list users"),
                shell.runService("shell:dpm list-owners"),
                shell.runService("shell:dumpsys account"));
    }

    private JSONObject begin(JSONObject params) throws Exception {
        discardActive();
        String transferId = requireUuid(params.getString("transferId"));
        long size = params.getLong("size");
        String expectedSha = requireSha(params.getString("sha256"));
        if (size <= 0L || size > MAX_APK_BYTES) {
            throw new IllegalArgumentException("Tamaño de APK fuera de límites.");
        }
        ensureStagingDirectory();
        File target = new File(stagingDirectory, transferId + ".apk");
        active = new Transfer(transferId, size, expectedSha, target);
        return new JSONObject().put("transferId", transferId).put("nextOffset", 0L);
    }

    private JSONObject append(JSONObject params) throws Exception {
        Transfer transfer = requireTransfer(params.getString("transferId"));
        long offset = params.getLong("offset");
        if (offset != transfer.received) {
            throw new IllegalArgumentException("Offset de chunk inválido.");
        }
        byte[] bytes;
        try {
            bytes = Base64.decode(params.getString("data"), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Chunk Base64 inválido.", error);
        }
        if (bytes.length == 0 || bytes.length > MAX_CHUNK_BYTES
                || transfer.received + bytes.length > transfer.expectedSize) {
            throw new IllegalArgumentException("Chunk fuera de límites.");
        }
        transfer.output.write(bytes);
        transfer.digest.update(bytes);
        transfer.received += bytes.length;
        return new JSONObject().put("transferId", transfer.id).put("nextOffset", transfer.received);
    }

    private JSONObject stage(JSONObject params) throws Exception {
        Transfer transfer = requireTransfer(params.getString("transferId"));
        if (transfer.received != transfer.expectedSize) {
            throw new IllegalStateException("La transferencia no está completa.");
        }
        transfer.finish();
        String actualSha = hex(transfer.digest.digest());
        if (!MessageDigest.isEqual(actualSha.getBytes(), transfer.expectedSha.getBytes())) {
            discardActive();
            throw new SecurityException("SHA-256 de APK no coincide.");
        }
        Artifact artifact = inspectArtifact(transfer.file);
        transfer.artifact = artifact;
        return artifactJson(transfer, artifact);
    }

    private JSONObject commit(JSONObject params) throws Exception {
        Transfer transfer = requireTransfer(params.getString("transferId"));
        if (!transfer.finished || transfer.artifact == null) {
            throw new IllegalStateException("La APK todavía no fue validada.");
        }
        String confirmedSigner = requireSha(params.getString("signerSha256"));
        if (!MessageDigest.isEqual(confirmedSigner.getBytes(), transfer.artifact.signerSha.getBytes())) {
            throw new SecurityException("La confirmación no corresponde al firmante staged.");
        }

        ProvisioningPreflight.Snapshot before = readPreflight();
        if (!before.eligible()) {
            throw new IllegalStateException(before.blockReason());
        }
        String installOutput = shell.installPackage(transfer.file).trim();
        if (!installOutput.toLowerCase(Locale.ROOT).contains("success")) {
            throw new IllegalStateException("Android rechazó la instalación: " + compact(installOutput));
        }
        verifyInstalledArtifact(transfer.artifact);

        boolean ownerCommandIssued = false;
        if (before.ownerState() == ProvisioningPreflight.OwnerState.NONE) {
            File receipt = new File(context.getFilesDir(), RECEIPT_NAME);
            if (!receipt.createNewFile()) {
                throw new SecurityException("Ya existe un intento de Device Owner registrado.");
            }
            ownerCommandIssued = true;
            String ownerOutput = shell.runService(
                    "shell:dpm set-device-owner " + ProvisioningPreflight.EXPECTED_COMPONENT).trim();
            ProvisioningPreflight.Snapshot afterAttempt = readPreflight();
            if (afterAttempt.ownerState() != ProvisioningPreflight.OwnerState.GLOSH) {
                throw new IllegalStateException("Device Owner no quedó activo: " + compact(ownerOutput));
            }
        }
        ProvisioningPreflight.Snapshot after = readPreflight();
        if (after.ownerState() != ProvisioningPreflight.OwnerState.GLOSH) {
            throw new IllegalStateException("Glosh no figura como Device Owner.");
        }
        JSONObject result = new JSONObject()
                .put("installed", true)
                .put("deviceOwner", true)
                .put("ownerCommandIssued", ownerCommandIssued)
                .put("packageName", transfer.artifact.packageName)
                .put("versionCode", transfer.artifact.versionCode);
        discardActive();
        return result;
    }

    private Artifact inspectArtifact(File apk) throws Exception {
        PackageManager pm = context.getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(
                apk.getAbsolutePath(), PackageManager.GET_RECEIVERS | PackageManager.GET_SIGNING_CERTIFICATES);
        if (info == null || !ProvisioningPreflight.EXPECTED_PACKAGE.equals(info.packageName)) {
            throw new SecurityException("La APK no es Glosh DEV.");
        }
        boolean hasReceiver = false;
        if (info.receivers != null) {
            for (ActivityInfo receiver : info.receivers) {
                hasReceiver |= ProvisioningPreflight.EXPECTED_RECEIVER.equals(receiver.name);
            }
        }
        if (!hasReceiver) {
            throw new SecurityException("La APK no declara el DeviceAdminReceiver esperado.");
        }
        Signature[] signers = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        if (signers == null || signers.length != 1) {
            throw new SecurityException("La APK debe tener exactamente un firmante actual.");
        }
        String signerSha = hex(MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray()));
        long version = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        return new Artifact(info.packageName, version, signerSha);
    }

    private void verifyInstalledArtifact(Artifact staged) throws Exception {
        PackageInfo installed = context.getPackageManager().getPackageInfo(
                ProvisioningPreflight.EXPECTED_PACKAGE,
                PackageManager.GET_RECEIVERS | PackageManager.GET_SIGNING_CERTIFICATES);
        boolean hasReceiver = false;
        if (installed.receivers != null) {
            for (ActivityInfo receiver : installed.receivers) {
                hasReceiver |= ProvisioningPreflight.EXPECTED_RECEIVER.equals(receiver.name);
            }
        }
        Signature[] signers = installed.signingInfo == null
                ? null : installed.signingInfo.getApkContentsSigners();
        String installedSigner = signers == null || signers.length != 1 ? ""
                : hex(MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray()));
        if (!hasReceiver || !staged.signerSha.equals(installedSigner)) {
            throw new SecurityException("La instalación no coincide con el artefacto validado.");
        }
    }

    private static JSONObject artifactJson(Transfer transfer, Artifact artifact) throws Exception {
        return new JSONObject()
                .put("transferId", transfer.id)
                .put("artifactSha256", transfer.expectedSha)
                .put("packageName", artifact.packageName)
                .put("versionCode", artifact.versionCode)
                .put("signerSha256", artifact.signerSha);
    }

    private void ensureStagingDirectory() throws IOException {
        if ((!stagingDirectory.exists() && !stagingDirectory.mkdirs()) || !stagingDirectory.isDirectory()) {
            throw new IOException("No se pudo crear staging privado.");
        }
    }

    private Transfer requireTransfer(String id) {
        String normalized = requireUuid(id);
        if (active == null || !active.id.equals(normalized)) {
            throw new IllegalStateException("Transferencia no activa.");
        }
        return active;
    }

    private static String requireUuid(String value) {
        return UUID.fromString(value).toString();
    }

    private static String requireSha(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(normalized).matches()) {
            throw new IllegalArgumentException("SHA-256 inválido.");
        }
        return normalized;
    }

    private static String compact(String value) {
        String compact = value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
        return compact.length() <= 240 ? compact : compact.substring(0, 240);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private void discardActive() {
        if (active == null) {
            return;
        }
        active.closeQuietly();
        if (active.file.exists()) {
            active.file.delete();
        }
        active = null;
    }

    @Override
    public synchronized void close() {
        discardActive();
    }

    private static final class Transfer {
        final String id;
        final long expectedSize;
        final String expectedSha;
        final File file;
        final MessageDigest digest;
        final FileOutputStream output;
        long received;
        boolean finished;
        Artifact artifact;

        Transfer(String id, long expectedSize, String expectedSha, File file) throws Exception {
            this.id = id;
            this.expectedSize = expectedSize;
            this.expectedSha = expectedSha;
            this.file = file;
            this.digest = MessageDigest.getInstance("SHA-256");
            this.output = new FileOutputStream(file, false);
        }

        void finish() throws IOException {
            if (!finished) {
                output.getFD().sync();
                output.close();
                finished = true;
            }
        }

        void closeQuietly() {
            try {
                output.close();
            } catch (IOException ignored) {
            }
        }
    }

    private record Artifact(String packageName, long versionCode, String signerSha) {
    }
}
