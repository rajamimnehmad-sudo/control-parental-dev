#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "hev-socks5-tunnel.h"

JNIEXPORT jint JNICALL
Java_com_contentfilter_feature_vpn_transport_HevNativeBridge_nativeRun(
    JNIEnv *env,
    jobject instance,
    jbyteArray config,
    jint tun_fd) {
    (void)instance;
    if (config == NULL || tun_fd < 0) {
        return -1;
    }
    jsize length = (*env)->GetArrayLength(env, config);
    if (length <= 0) {
        return -1;
    }
    unsigned char *copy = malloc((size_t)length);
    if (copy == NULL) {
        return -1;
    }
    (*env)->GetByteArrayRegion(env, config, 0, length, (jbyte *)copy);
    if ((*env)->ExceptionCheck(env)) {
        free(copy);
        return -1;
    }
    int result = hev_socks5_tunnel_main_from_str(copy, (unsigned int)length, tun_fd);
    memset(copy, 0, (size_t)length);
    free(copy);
    return result;
}

JNIEXPORT void JNICALL
Java_com_contentfilter_feature_vpn_transport_HevNativeBridge_nativeQuit(
    JNIEnv *env,
    jobject instance) {
    (void)env;
    (void)instance;
    hev_socks5_tunnel_quit();
}

JNIEXPORT jlongArray JNICALL
Java_com_contentfilter_feature_vpn_transport_HevNativeBridge_nativeStats(
    JNIEnv *env,
    jobject instance) {
    (void)instance;
    size_t tx_packets = 0;
    size_t tx_bytes = 0;
    size_t rx_packets = 0;
    size_t rx_bytes = 0;
    jlong values[4];
    hev_socks5_tunnel_stats(&tx_packets, &tx_bytes, &rx_packets, &rx_bytes);
    values[0] = (jlong)tx_packets;
    values[1] = (jlong)tx_bytes;
    values[2] = (jlong)rx_packets;
    values[3] = (jlong)rx_bytes;
    jlongArray result = (*env)->NewLongArray(env, 4);
    if (result != NULL) {
        (*env)->SetLongArrayRegion(env, result, 0, 4, values);
    }
    return result;
}
