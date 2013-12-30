package com.github.louisbros.server;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.iharder.Base64;

public class SimpleWebsocketServer implements Runnable, Serializable{

	private static final long serialVersionUID = 1L;
	private volatile static SimpleWebsocketServer server;
	private transient Thread thread;
	private static final String MAGIC_STRING = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
	private List<Peer> peers;
	
	private SimpleWebsocketServer(){
		peers = new ArrayList<Peer>();
	}
	
	public static SimpleWebsocketServer getInstance(){
		
		if(server == null){
			synchronized(SimpleWebsocketServer.class){
				if(server == null){
					server = new SimpleWebsocketServer();
					server.start();
				}
			}
		}
		
		return server;
	}
	
	public void start(){
		thread = new Thread(server);
		thread.start();
	}
	
	public void stop(){
		thread.interrupt();
	}
	
	//@Override
	public void run() {
		
		ServerSocketChannel server = null;
		Selector selector = null;
		
		try{
			
			server = ServerSocketChannel.open();
			server.configureBlocking( false );
			ServerSocket socket = server.socket();
			socket.bind(new InetSocketAddress("127.0.0.1", 8889));
			selector = Selector.open();
			server.register( selector, SelectionKey.OP_ACCEPT );
			
			while(!thread.isInterrupted()){
				
				selector.select(500);
				
				Iterator<SelectionKey> i = selector.selectedKeys().iterator();
				while(i.hasNext()){
					
					SelectionKey selectionKey = i.next();
					i.remove();
					
					if(selectionKey.isAcceptable()){
						
						SocketChannel channel = server.accept();
						channel.configureBlocking( false );
						Peer peer = new Peer(channel);
						peers.add(peer);
						channel.register( selector, SelectionKey.OP_READ, peer);
					}

					if(selectionKey.isReadable() && selectionKey.attachment() instanceof Peer){
						
						Peer peer = (Peer)selectionKey.attachment();
						StringBuilder sb = new StringBuilder();
						
						if(readChannel(peer, sb)){

							if(!peer.isHandshakeComplete()){
								
								String[] lines = sb.toString().split("\\n");
								for(String line: lines){
									String[] keyVal = line.split(":");
									if(keyVal.length == 2 && keyVal[0].equals("Sec-WebSocket-Key")){
										peer.setKey(keyVal[1].trim());
									}
								}
							}
						}
						else{
							peer.getChannel().close();
							peers.remove(peer);
							continue;
						}
						
						selectionKey.interestOps(SelectionKey.OP_WRITE);
					}
					
					if(selectionKey.isWritable() && selectionKey.attachment() instanceof Peer){

						Peer peer = (Peer)selectionKey.attachment();
						
						if(!peer.isHandshakeComplete()){
							
							peer.setHandshakeComplete(true);
							
							String base64 = Base64.encodeBytes(MessageDigest.getInstance("SHA1").digest((peer.getKey()+MAGIC_STRING).getBytes()));
				            String response =
				            	"HTTP/1.1 101 Switching Protocols\r\n" +
						        "Connection: Upgrade\r\n" +
				            	"Sec-WebSocket-Accept: "+base64+"\r\n" +
				            	"Upgrade: websocket\r\n" +
				            	"\r\n"
				            ;
				            writeChannel(peer, response);
						}
						else{
							writeChannel(peer, "Handshake Complete");
						}

						selectionKey.interestOps(SelectionKey.OP_READ);
					}
				}
			}
		}
		catch(IOException e){
			e.printStackTrace();
		}
		catch(NoSuchAlgorithmException e){
			e.printStackTrace();
		}
		finally{
			try{
				if(server != null ){
					server.close();
				}
				if(selector != null ){
					selector.close();
				}
			}
			catch(IOException e){
				e.printStackTrace();
			}
		}
	}
	
	private boolean readChannel(Peer peer, StringBuilder sb) throws IOException{

		ByteBuffer buf = ByteBuffer.allocate(1024);
		int read = peer.getChannel().read(buf);
		
		buf.flip();
		
		List<Byte> frame = new ArrayList<Byte>(read);
		for(int i = 0;i < read;i++){
			byte b = buf.get(i);
			frame.add(b);
			sb.append((char)(b & 0xff));
		}
		
		if(peer.isHandshakeComplete()){ // frame
			sb.append(decodeFrame(frame));
		}

		return read != -1 ? true : false;
	}
	
	private void writeChannel(Peer peer, String message) throws IOException{
		
		ByteBuffer buf = null;
		
		if(peer.isHandshakeComplete()){ // frame
			
			List<Byte> frame = encodeFrame(message);
			buf = ByteBuffer.allocate(frame.size());
			for(byte b : frame){
				buf.put(b);
			}
		}
		else{
			buf = ByteBuffer.allocate(message.getBytes().length);
			buf.put(message.getBytes());
		}

		buf.flip();
		

		while(buf.hasRemaining()) {
			peer.getChannel().write(buf);
		}
	}
	
	// masked
	private String decodeFrame(List<Byte> frame){

		StringBuilder sb = new StringBuilder();
		
		Byte type = frame.remove(0); // don't really need this, only want to deal with text for now
		Byte length = (byte)(frame.remove(0) & 127);
		//TODO: detect whether the length is stored in 1, 2 or 8 bytes
		List<Byte> masks = frame.subList(0, 4);
		List<Byte> data = frame.subList(4, frame.size());
		
		for(int i = 0;i < length;i++){
			sb.append((char)(data.get(i) ^ masks.get(i % masks.size())));
		}

		encodeFrame(sb.toString());
		
		return sb.toString();
	}
	
	// unmasked
	private List<Byte> encodeFrame(String message){
		
		List<Byte> frame = new ArrayList<Byte>();
		frame.add((byte)Integer.parseInt("10000001", 2));
		frame.add((byte)message.length());
		for(int i = 0;i < message.length();i++){
			frame.add((byte)message.charAt(i));
		}

		return frame;
	}
}
