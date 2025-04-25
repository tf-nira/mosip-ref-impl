package io.mosip.commons.packet.cache.provider.redis.config;

import java.security.KeyStore;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import java.io.FileInputStream;
import redis.clients.jedis.Jedis;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

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
	
	@Value("${redis.cache.sni}")
    private String sni;

	@Bean
	JedisConnectionFactory jedisConnectionFactory() {
		
		  RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
  	    redisConfig.setHostName(hostname);
  	    redisConfig.setPort(port);
  	  //  redisConfig.setUsername(username);
  	    redisConfig.setPassword(RedisPassword.of(password));
  	    try {
  	        // Load CA certificate into a TrustStore
  	        KeyStore trustStore = KeyStore.getInstance("JKS");
  	        FileInputStream fis = new FileInputStream(certPath);
  	        trustStore.load(fis, keystorePassword.toCharArray());

  	        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
  	        tmf.init(trustStore);

  	        // Create SSL Context
  	        SSLContext sslContext = SSLContext.getInstance("TLS");
  	        sslContext.init(null, tmf.getTrustManagers(), new java.security.SecureRandom());

  	        SSLSocketFactory baseFactory = sslContext.getSocketFactory();

  	        // 🔐 Custom SSLSocketFactory that sets SNI
  	        SSLSocketFactory sniFactory = new SSLSocketFactory() {
  	            @Override
  	            public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
  	                SSLSocket socket = (SSLSocket) baseFactory.createSocket(s, host, port, autoClose);
  	                injectSni(socket);
  	                return socket;
  	            }

  	            @Override
  	            public Socket createSocket(String host, int port) throws IOException {
  	                SSLSocket socket = (SSLSocket) baseFactory.createSocket(host, port);
  	                injectSni(socket);
  	                return socket;
  	            }

  	            @Override
  	            public Socket createSocket(InetAddress host, int port) throws IOException {
  	                SSLSocket socket = (SSLSocket) baseFactory.createSocket(host, port);
  	                injectSni(socket);
  	                return socket;
  	            }

  	            @Override
  	            public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
  	                SSLSocket socket = (SSLSocket) baseFactory.createSocket(host, port, localHost, localPort);
  	                injectSni(socket);
  	                return socket;
  	            }

  	            @Override
  	            public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
  	                SSLSocket socket = (SSLSocket) baseFactory.createSocket(address, port, localAddress, localPort);
  	                injectSni(socket);
  	                return socket;
  	            }

  	            private void injectSni(SSLSocket socket) {
  	                SSLParameters sslParams = socket.getSSLParameters();
  	                List<SNIServerName> sniHostNames = Collections.singletonList(
  	                        new SNIHostName(sni)); // <-- your SNI
  	                sslParams.setServerNames(sniHostNames);
  	                socket.setSSLParameters(sslParams);
  	            }

					@Override
					public String[] getDefaultCipherSuites() {
						// TODO Auto-generated method stub
						return null;
					}

					@Override
					public String[] getSupportedCipherSuites() {
						// TODO Auto-generated method stub
						return null;
					}
  	        };

  	        JedisClientConfiguration clientConfig = JedisClientConfiguration.builder()
  	                .useSsl()
  	                .sslSocketFactory(sniFactory)
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
  	        return jedisConnectionFactory;

  	    } catch (Exception e) {
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
