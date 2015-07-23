package com.cocoons.actor;

import java.text.MessageFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import com.cocoons.harbor.HarborServer;

/**
 *
 * @author qinguofeng
 */
public class ActorSystem {
	private Map<String, ActorRef> actorsRefMap = new ConcurrentHashMap<>();
	private Map<String, Actor> actorsMap = new ConcurrentHashMap<>();
	private LinkedBlockingQueue<Actor> actors = new LinkedBlockingQueue<>();

	private AtomicLong sessionIndex = new AtomicLong(0L);

	private String systemName;
	private String harborName;

	public ActorSystem(String name) {
		this.systemName = name;
	}

	private void doWork() {
		for (;;) {
			try {
				// System.out.println("dispatch in "
				// + Thread.currentThread().getId());
				Actor actor = actors.take();
				// try {
				if (actor != null) {
					actor.dispatch();
				}
				// } finally {
				// actors.add(actor);
				// }
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private String wrapActorName(String actorName) {
		return MessageFormat.format("{0}:{1}", systemName, actorName);
	}

	public ActorRef actor(String name, Actor actor) {
		name = wrapActorName(name);

		actor.setContext(name, this);
		actorsMap.put(name, actor);
		// actors.add(actor);
		ActorRef ref = new ActorRef(name, this);
		actorsRefMap.put(name, ref);
		return ref;
	}

	public ActorRef remoteActorOf(String remoteName) {
		ActorRef ref = actorsRefMap.get(remoteName);
		if (ref == null) {
			ref = new ActorRef(remoteName, this);
			actorsRefMap.put(remoteName, ref);
		}
		return ref;
	}

	private boolean isLocalActor(String name) {
		// TODO optimize... 采用数字ID,第一位表示本地还是远程服务,这样只要做与运算就可以判断
		final String systemNamePrefix = MessageFormat
				.format("{0}:", systemName);
		return name.startsWith(systemNamePrefix);
	}

	public ActorRef getActorRefOf(String name) {
		if (name == null) {
			return null;
		}

		ActorRef ref = actorsRefMap.get(name);
		if (ref == null && !isLocalActor(name)) {
			ref = remoteActorOf(name);
		}
		return ref;
	}

	private void addActorToGlobalQueue(Actor actor) {
		actors.add(actor);
	}

	public void sendMsgTo(String name, ActorMessage msg) {
		if (isLocalActor(name)) { // local message
			Actor actor = actorsMap.get(name);
			if (actor == null) {
				throw new IllegalStateException(name + " actor not exist.");
			}
			actor.addMessage(msg);
			if (!actor.isInGlobalQueue()) {
				if (actor.putToGlobalQueue(false, true)) {
					addActorToGlobalQueue(actor);
				}
			}
		} else { // remote message
			Actor harbor = actorsMap.get(harborName);
			if (harbor == null) {
				throw new IllegalStateException("no harbor started.");
			}
			harbor.addMessage(ActorMessage.wrapHarborMessage(harborName,
					"sendRemote", msg));
			if (!harbor.isInGlobalQueue()) {
				if (harbor.putToGlobalQueue(false, true)) {
					addActorToGlobalQueue(harbor);
				}
			}
		}
	}

	public String getSid() {
//		return MessageFormat.format("{0}:{1}", systemName,
//				sessionIndex.incrementAndGet());
//		return sessionIndex.incrementAndGet() + "";
		return "";
	}

	public void startHarborService(int port) {
		harborName = wrapActorName(HarborServer.HARBOR);
		ActorRef ref = actor(HarborServer.HARBOR, new HarborServer());
		ref.send(null, new MessageEntity("startHarbor", port));
	}

	public void start(int threadNum) {
		ExecutorService executor = Executors.newFixedThreadPool(threadNum);
		for (int i = 0; i < threadNum; i++) {
			executor.submit(this::doWork);
		}
	}
}
