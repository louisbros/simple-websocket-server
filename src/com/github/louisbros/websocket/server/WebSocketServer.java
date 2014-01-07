package com.github.louisbros.websocket.server;

import java.io.IOException;
import java.io.Serializable;

public interface WebSocketServer extends Runnable{

	void start();
	void stop();
	int getPort();
	void broadcast(String message) throws IOException;
}
