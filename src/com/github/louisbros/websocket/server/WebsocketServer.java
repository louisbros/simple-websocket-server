package com.github.louisbros.websocket.server;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class WebsocketServer implements Runnable, Serializable{

	private static final long serialVersionUID = 1L;
	private volatile static WebsocketServer server;
	private transient Thread thread;
	private List<Peer> peers;
	
	private WebsocketServer(){
		peers = new ArrayList<Peer>();
	}
	
	public static WebsocketServer getInstance(){
		
		if(server == null){
			synchronized(WebsocketServer.class){
				if(server == null){
					server = new WebsocketServer();
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
					else if(selectionKey.isReadable() && selectionKey.attachment() instanceof Peer){
						
						Peer peer = (Peer)selectionKey.attachment();
						ByteBuffer buffer = ByteBuffer.allocate(1024);
						int read = peer.getChannel().read(buffer);
						
						if(read == -1){
							peer.getChannel().close();
							peers.remove(peer);
							continue;
						}

						buffer.flip();
						
						if(!peer.isHandshakeComplete()){
							peer.setHandshakeProperties(ProtocolUtils.readHandshake(buffer));
						}
						else{
							System.out.println(ProtocolUtils.decodeMaskedFrame(buffer));
						}
						
						selectionKey.interestOps(SelectionKey.OP_WRITE);
					}
					else if(selectionKey.isWritable() && selectionKey.attachment() instanceof Peer){

						Peer peer = (Peer)selectionKey.attachment();
						
						if(!peer.isHandshakeComplete()){
							peer.setHandshakeComplete(true);
							ProtocolUtils.writeHandshake(peer.getChannel(), peer.getHandshakeProperties().getProperty("Sec-WebSocket-Key"));
						}
						else{
							ByteBuffer buffer = ProtocolUtils.encodeUnmaskedFrame("Handshake Complete");
							buffer.flip();
							while(buffer.hasRemaining()){
								peer.getChannel().write(buffer);
							}
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
}
