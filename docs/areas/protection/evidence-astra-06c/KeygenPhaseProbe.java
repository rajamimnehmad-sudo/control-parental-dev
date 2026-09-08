import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
public final class KeygenPhaseProbe {
  public static void main(String[] args) throws Exception {
    for (String algorithm : new String[]{"RSA", "EC"}) {
      KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
      if (algorithm.equals("RSA")) generator.initialize(2048);
      else generator.initialize(new ECGenParameterSpec("secp256r1"));
      for (int i=0;i<6;i++) {
        long start=System.nanoTime();
        generator.generateKeyPair();
        System.out.println(algorithm+" keygenMs="+((System.nanoTime()-start)/1000000.0));
      }
    }
  }
}
