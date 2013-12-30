package com.github.louisbros.websocket.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import net.iharder.Base64;

public class ProtocolUtils {

	private static final String WS_MAGIC_STRING = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
	
	public static Properties readHandshake(ByteBuffer buffer){
		
		Properties props = new Properties();
		
		StringBuilder sb = new StringBuilder();
		while(buffer.hasRemaining()){
			sb.append((char)(buffer.get() & 0xff));
		}
		
		String[] lines = sb.toString().split("\\n");
		for(String line: lines){
			String[] keyVal = line.split(":");
			if(keyVal.length == 2){
				props.put(keyVal[0].trim(), keyVal[1].trim());
			}
		}
		
		return props;
	}
	
	public static void writeHandshake(SocketChannel channel, String clientKey) throws NoSuchAlgorithmException, IOException{
		
        String message =
        	"HTTP/1.1 101 Switching Protocols\r\n" +
	        "Connection: Upgrade\r\n" +
        	"Sec-WebSocket-Accept: "+Base64.encodeBytes(MessageDigest.getInstance("SHA1").digest((clientKey+WS_MAGIC_STRING).getBytes()))+"\r\n" +
        	"Upgrade: websocket\r\n" +
        	"\r\n"
        ;
        
        ByteBuffer buffer = ByteBuffer.allocate(message.getBytes().length);
        buffer.put(message.getBytes());
        buffer.flip();
		while(buffer.hasRemaining()) {
			channel.write(buffer);
		}
	}
	
	public static String decodeMaskedFrame(ByteBuffer buffer){
		
		List<Byte> frame = new ArrayList<Byte>();
		while(buffer.hasRemaining()){
			frame.add(buffer.get());
		}

		StringBuilder sb = new StringBuilder();
		
		Byte type = frame.remove(0); // don't really need this, only want to deal with text for now
		Byte length = (byte)(frame.remove(0) & 127);
		//TODO: detect whether the length is stored in 1, 2 or 8 bytes
		List<Byte> masks = frame.subList(0, 4);
		List<Byte> data = frame.subList(4, frame.size());
		
		for(int i = 0;i < length;i++){
			sb.append((char)(data.get(i) ^ masks.get(i % masks.size())));
		}
		
		return sb.toString();
	}
	
	public static ByteBuffer encodeUnmaskedFrame(String message){
		
		List<Byte> frame = new ArrayList<Byte>();
		frame.add((byte)Integer.parseInt("10000001", 2));
		frame.add((byte)message.length());
		for(int i = 0;i < message.length();i++){
			frame.add((byte)message.charAt(i));
		}
		
		ByteBuffer buffer = ByteBuffer.allocate(frame.size());
		for(byte b : frame){
			buffer.put(b);
		}
		
		return buffer;
	}
}
