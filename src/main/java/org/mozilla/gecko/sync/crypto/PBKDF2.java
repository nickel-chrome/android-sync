/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.sync.crypto;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.PBEKeySpec;

import org.apache.commons.lang3.SystemUtils;
import org.mozilla.gecko.background.common.log.Logger;

public class PBKDF2 {
  private static final String LOG_TAG = "PBKDF2";
  
  private static String cryptoProvider = null;
	
  public static String getCryptoProvider() {
    return cryptoProvider;
  }

  public static void setCryptoProvider(String provider) {
    cryptoProvider = provider;
  }
		
  public static byte[] pbkdf2SHA256(byte[] password, byte[] salt, int c, int dkLen) throws UnsupportedEncodingException, NoSuchMethodException, GeneralSecurityException {
	Logger.debug(LOG_TAG, "pbkdf2SHA256()");
	Logger.info(LOG_TAG, String.format("PBKDF2 iterations: %d, key length: %d", c, dkLen));
	
	Logger.debug(LOG_TAG, "Java version: " + SystemUtils.JAVA_VERSION);
	
	Long startTime = System.currentTimeMillis();
	
	byte[] pbkdf2Digest = null;
	
    if ( cryptoProvider != null ) {
      Logger.info(LOG_TAG, "pbkdf2SHA256 " + cryptoProvider);
      
      String providerPrefix = null;	
      if ( cryptoProvider.equalsIgnoreCase("SC") ) { 
        providerPrefix = "org.spongycastle";
      } else {
        providerPrefix = "org.bouncycastle";    	  
      }
      
      //Call Bouncy Castle/Spongy Castle implementation using reflection

      //PKCS5S2ParametersGenerator generator = new PKCS5S2ParametersGenerator(new SHA256Digest());
  	  //generator.init(PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(password), salt, iterations);
  	  //KeyParameter key = (KeyParameter)generator.generateDerivedMacParameters(keySizeInBits);
  	 		
      try {
        Class<?> generatorClass     = Class.forName(providerPrefix + ".crypto.generators.PKCS5S2ParametersGenerator");
        Class<?> generatorSupClass  = Class.forName(providerPrefix + ".crypto.PBEParametersGenerator");
        Class<?> digestClass        = Class.forName(providerPrefix + ".crypto.Digest");
        Class<?> sha256DigestClass  = Class.forName(providerPrefix + ".crypto.digests.SHA256Digest");
        Class<?> keyParameterClass  = Class.forName(providerPrefix + ".crypto.params.KeyParameter");

        Constructor<?> generatorConstructor = generatorClass.getConstructor(digestClass);
        Object generatorInstance = generatorConstructor.newInstance(sha256DigestClass.newInstance());

        Method generatorInit = generatorSupClass.getDeclaredMethod("init", byte[].class, byte[].class, int.class);
        generatorInit.invoke(generatorInstance, password, salt, c);

        Method generatorDeriveKey = generatorClass.getDeclaredMethod("generateDerivedMacParameters", int.class);
        Object keyParameterInstance = keyParameterClass.cast(generatorDeriveKey.invoke(generatorInstance, dkLen*8)); //Note dkLen is in bytes

        Method keyParameterGetKey = keyParameterClass.getDeclaredMethod("getKey");
        pbkdf2Digest = (byte[])keyParameterGetKey.invoke(keyParameterInstance);
			
      } catch (Exception e) {
        throw new NoSuchMethodException(e.getClass().getName() + ": " + e.getMessage());
      }

    } else if ( SystemUtils.IS_JAVA_1_8 ) {
      Logger.info(LOG_TAG, "pbkdf2SHA256 JCE");
      
  	  //Call PBKDF2WithHmacSHA256 from Java Security implementation available in Java 1.8
    	
      //FIXME - Make sure JCE Char Array password parameter is equivalent to UTF encoded password
      //String passwordString = new String(password, "UTF-8");
      //char[] passwordCharArray = passwordString.toCharArray();
      char[] passwordCharArray = new char[password.length];      
      for (int i = 0; i < password.length; i++) {
        passwordCharArray[i] = (char)password[i];
      }
      
      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      KeySpec spec = new PBEKeySpec(passwordCharArray, salt, c, dkLen*8); //Note dkLen is in bytes
      pbkdf2Digest = factory.generateSecret(spec).getEncoded();

    } else {
      Logger.info(LOG_TAG, "pbkdf2SHA256 MOZ");
      
      //This is very slow, i.e. 10min on Motorola RAZR M, we really don't want to be using this implementation
      pbkdf2Digest = MozPbkdf2SHA256(password, salt, c, dkLen);
	}
    
    Logger.info(LOG_TAG, String.format("PBKDF2 execution time: %dms", (System.currentTimeMillis() - startTime)));
    
    return pbkdf2Digest;
  }  
  
  public static byte[] MozPbkdf2SHA256(byte[] password, byte[] salt, int c, int dkLen)
      throws GeneralSecurityException {
    final String algorithm = "HmacSHA256";
    SecretKeySpec keyspec = new SecretKeySpec(password, algorithm);
    Mac prf = Mac.getInstance(algorithm);
    prf.init(keyspec);

    int hLen = prf.getMacLength();

    byte U_r[] = new byte[hLen];
    byte U_i[] = new byte[salt.length + 4];
    byte scratch[] = new byte[hLen];

    int l = Math.max(dkLen, hLen);
    int r = dkLen - (l - 1) * hLen;
    byte T[] = new byte[l * hLen];
    int ti_offset = 0;
    for (int i = 1; i <= l; i++) {
      Arrays.fill(U_r, (byte) 0);
      F(T, ti_offset, prf, salt, c, i, U_r, U_i, scratch);
      ti_offset += hLen;
    }

    if (r < hLen) {
      // Incomplete last block.
      byte DK[] = new byte[dkLen];
      System.arraycopy(T, 0, DK, 0, dkLen);
      return DK;
    }

    return T;
  }

  private static void F(byte[] dest, int offset, Mac prf, byte[] S, int c, int blockIndex, byte U_r[], byte U_i[], byte[] scratch)
      throws ShortBufferException, IllegalStateException {
    final int hLen = prf.getMacLength();

    // U0 = S || INT (i);
    System.arraycopy(S, 0, U_i, 0, S.length);
    INT(U_i, S.length, blockIndex);

    for (int i = 0; i < c; i++) {
      prf.update(U_i);
      prf.doFinal(scratch, 0);
      U_i = scratch;
      xor(U_r, U_i);
    }

    System.arraycopy(U_r, 0, dest, offset, hLen);
  }

  private static void xor(byte[] dest, byte[] src) {
    for (int i = 0; i < dest.length; i++) {
      dest[i] ^= src[i];
    }
  }

  private static void INT(byte[] dest, int offset, int i) {
    dest[offset + 0] = (byte) (i / (256 * 256 * 256));
    dest[offset + 1] = (byte) (i / (256 * 256));
    dest[offset + 2] = (byte) (i / (256));
    dest[offset + 3] = (byte) (i);
  }
}
