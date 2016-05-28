package com.github.pires.example.web;

import javax.servlet.http.HttpServletRequest;

import org.apache.shiro.codec.Base64;
import org.apache.shiro.crypto.AesCipherService;
import org.apache.shiro.crypto.CipherService;
import org.apache.shiro.io.DefaultSerializer;
import org.apache.shiro.io.Serializer;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.ByteSource;
import org.apache.shiro.web.servlet.Cookie;
import org.apache.shiro.web.servlet.SimpleCookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CookieUtils {

	private static final transient Logger log = LoggerFactory.getLogger(SimpleCookie.class);

	public static String readValue(HttpServletRequest request, String name) {
		String value = null;
		javax.servlet.http.Cookie cookie = getCookie(request, name);
		if (cookie != null) {
			value = cookie.getValue();
			log.debug("Found '{}' cookie value [{}]", name, value);
		} else {
			log.trace("No '{}' cookie value", name);
		}

		return value;
	}

	/**
	 * Returns the cookie with the given name from the request or {@code null}
	 * if no cookie with that name could be found.
	 *
	 * @param request the current executing http request.
	 * @param cookieName the name of the cookie to find and return.
	 * @return the cookie with the given name from the request or {@code null}
	 *         if no cookie with that name could be found.
	 */
	private static javax.servlet.http.Cookie getCookie(HttpServletRequest request, String cookieName) {
		javax.servlet.http.Cookie cookies[] = request.getCookies();
		if (cookies != null) {
			for (javax.servlet.http.Cookie cookie : cookies) {
				if (cookie.getName().equals(cookieName)) {
					return cookie;
				}
			}
		}
		return null;
	}

	/**
	 * The following Base64 string was generated by auto-generating an AES Key:
	 * 
	 * <pre>
	 * AesCipherService aes = new AesCipherService();
	 * byte[] key = aes.generateNewKey().getEncoded();
	 * String base64 = Base64.encodeToString(key);
	 * </pre>
	 * 
	 * The value of 'base64' was copied-n-pasted here:
	 */
	private static final byte[] DEFAULT_CIPHER_KEY_BYTES = Base64.decode("kPH+bIxk5D2deZiIxcaaaA==");
	private static byte[] decryptionCipherKey = DEFAULT_CIPHER_KEY_BYTES;
	private static CipherService cipherService = new AesCipherService();
	private static Serializer<PrincipalCollection> serializer = new DefaultSerializer<PrincipalCollection>();;

	public static String getRememberMeInfo(HttpServletRequest request, String name) {
		String base64 = readValue(request, name);
		// Browsers do not always remove cookies immediately (SHIRO-183)
		// ignore cookies that are scheduled for removal
		if (Cookie.DELETED_COOKIE_VALUE.equals(base64))
			return null;

		if (base64 != null) {
			base64 = ensurePadding(base64);
			if (log.isTraceEnabled()) {
				log.trace("Acquired Base64 encoded identity [" + base64 + "]");
			}
			byte[] decoded = Base64.decode(base64);
			if (log.isTraceEnabled()) {
				log.trace("Base64 decoded byte array length: " + (decoded != null ? decoded.length : 0) + " bytes.");
			}
			// SHIRO-138 - only call convertBytesToPrincipals if bytes exist:
			if (decoded != null && decoded.length > 0) {
				if (getCipherService() != null) {
					decoded = decrypt(decoded);
				}
				PrincipalCollection principals = deserialize(decoded);
				if (principals != null) {
					return (String) principals.getPrimaryPrincipal();
				}
			}
			return null;
		} else {
			// no cookie set - new site visitor?
			return null;
		}
	}

	/**
	 * De-serializes the given byte array by using the {@link #getSerializer()
	 * serializer}'s {@link Serializer#deserialize deserialize} method.
	 *
	 * @param serializedIdentity the previously serialized
	 *            {@code PrincipalCollection} as a byte array
	 * @return the de-serialized (reconstituted) {@code PrincipalCollection}
	 */
	protected static PrincipalCollection deserialize(byte[] serializedIdentity) {
		return getSerializer().deserialize(serializedIdentity);
	}

	private static Serializer<PrincipalCollection> getSerializer() {
		return serializer;
	}

	private static CipherService getCipherService() {
		return cipherService;
	}

	/**
	 * Decrypts the byte array using the configured {@link #getCipherService()
	 * cipherService}.
	 *
	 * @param encrypted the encrypted byte array to decrypt
	 * @return the decrypted byte array returned by the configured
	 *         {@link #getCipherService () cipher}.
	 */
	protected static byte[] decrypt(byte[] encrypted) {
		byte[] serialized = encrypted;
		CipherService cipherService = getCipherService();
		if (cipherService != null) {
			ByteSource byteSource = cipherService.decrypt(encrypted, getDecryptionCipherKey());
			serialized = byteSource.getBytes();
		}
		return serialized;
	}

	private static byte[] getDecryptionCipherKey() {
		return decryptionCipherKey;
	}

	/**
	 * Sometimes a user agent will send the rememberMe cookie value without
	 * padding, most likely because {@code =} is a separator in the cookie
	 * header.
	 * <p/>
	 * Contributed by Luis Arias. Thanks Luis!
	 *
	 * @param base64 the base64 encoded String that may need to be padded
	 * @return the base64 String padded if necessary.
	 */
	private static String ensurePadding(String base64) {
		int length = base64.length();
		if (length % 4 != 0) {
			StringBuilder sb = new StringBuilder(base64);
			for (int i = 0; i < length % 4; ++i) {
				sb.append('=');
			}
			base64 = sb.toString();
		}
		return base64;
	}
}