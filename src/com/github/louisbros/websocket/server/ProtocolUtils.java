package com.github.louisbros.websocket.server;

import java.io.IOException;
import java.math.BigInteger;
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
	
	enum Code {

		TEXT_MESSAGE(1),
		BINARY_MESSAEG(2),
		CLOSE(8);
		
		private int code;
		
		Code(int code){
			this.code = code;
		}
		
		public int getCode(){
			return code;
		}
	}
	
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
		
		StringBuilder sb = new StringBuilder();
		
		List<Byte> frame = new ArrayList<Byte>();
		while(buffer.hasRemaining()){
			frame.add(buffer.get());
		}
		
		Byte code = (byte)(frame.remove(0) & 127 & 0xff);

		if(code == Code.TEXT_MESSAGE.getCode()){
			
			int length = (int) frame.remove(0) & 127 & 0xff; // 7 bits
			if(length == 126){ // 16 bits
				length = (int) frame.remove(0) & 0xff;
				length += (int) frame.remove(0) & 0xff;
			}
			if(length == 127){ // 64 bits
				length = (int) frame.remove(0) & 0xff;
				length += (int) frame.remove(0) & 0xff;
				length += (int) frame.remove(0) & 0xff;
				length += (int) frame.remove(0) & 0xff;
				length += (int) frame.remove(0) & 0xff;
				length += (int) frame.remove(0) & 0xff;
				length += (int) frame.remove(0) & 0xff;
				length += (int) frame.remove(0) & 0xff;
			}
			
			List<Byte> masks = frame.subList(0, 4);
			List<Byte> data = frame.subList(4, frame.size());
			for(int i = 0;i < length;i++){
				sb.append((char)(data.get(i) ^ masks.get(i % masks.size())));
			}
		}
		else if(code == Code.CLOSE.getCode()){
			sb.append(Code.CLOSE.name());
		}
		else{
			// throw new unsupported code exception
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
