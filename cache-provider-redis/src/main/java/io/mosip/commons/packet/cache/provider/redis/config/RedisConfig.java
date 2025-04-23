package io.mosip.commons.packet.cache.provider.redis.config;

import java.security.KeyStore;
import java.util.Scanner;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import java.io.FileInputStream;
import redis.clients.jedis.Jedis;

@Configuration
public class RedisConfig {

	@Value("${redis.cache.hostname}")
	private String hostname;

	@Value("${redis.cache.port}")
	private int port;

	@Value("${redis.cache.username}")
	private String username;

	@Value("${redis.cache.password}")
	private String password;
	
	@Value("${redis.cache.certPath}")
	private String certPath;
	
	@Value("${redis.cache.keystorePassword}")
	private String keystorePassword;

	@Bean
	JedisConnectionFactory jedisConnectionFactory() {
		
		RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
		redisConfig.setHostName(hostname); // Replace with actual host
		redisConfig.setPort(port); // Redis port
		redisConfig.setUsername(username); // Redis username
		redisConfig.setPassword(password); // Redis password

		try {
			// Load CA certificate into a TrustStore
			KeyStore trustStore = KeyStore.getInstance("JKS");
			FileInputStream fis = new FileInputStream(certPath); // Path to your TrustStore
			trustStore.load(fis, keystorePassword.toCharArray()); // TrustStore password

			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(trustStore);

			// Create SSL Context
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, tmf.getTrustManagers(), new java.security.SecureRandom());
			SSLParameters sslParameters = new SSLParameters();
			sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
			
			JedisClientConfiguration clientConfig = JedisClientConfiguration.builder().useSsl()
					.sslSocketFactory(sslContext.getSocketFactory()) // Custom SSLSocketFactory
					.build();

			JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory(redisConfig, clientConfig);
			jedisConnectionFactory.afterPropertiesSet();
			
	        // Test the connection
	        try (Jedis jedis = (Jedis) jedisConnectionFactory.getConnection().getNativeConnection()) {
	            jedis.set("testKey", "Hello Redis!"); // Set a key-value pair
	            String value = jedis.get("testKey");  // Retrieve the value
	            System.out.println("Stored value in Redis: " + value);

	            System.out.println("Redis connection test successful!");
	        } catch (Exception e) {
	            System.err.println("Redis connection test failed: " + e.getMessage());
	        }
	        
			return new JedisConnectionFactory(redisConfig, clientConfig);

		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
			return null;
		}
	}

	@Bean
	public RedisTemplate<String, Object> redisTemplate() {
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(jedisConnectionFactory());
		return template;
	}
}
