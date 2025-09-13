package githubcew.arguslog.common.util;


import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

/**
 * RSA工具
 *
 * @author chenenwei
 */

public class RSAUtil {

    /**
     * 使用私钥解密数据
     *
     * @param encryptedBase64 前端发送的、Base64编码的密文
     * @param privateKey      用于解密的私钥
     * @return 解密后的明文字符串
     */
    public static String decryptWithPrivateKey(String encryptedBase64, PrivateKey privateKey) {
        try {

            // Base64解码
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedBase64);

            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");

            // 明确指定OAEP参数
            OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    PSource.PSpecified.DEFAULT
            );

            cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParams);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            e.printStackTrace();
            //
        }
        return "";
    }
}