package com.sshtools.jini.config;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A file monitoring service. Several subsystems such as {@link Shell},
 * {@link Themes} and {@link Configuration} all use this via an {@link INISet}
 * to monitor for file changes.
 * <p>
 * The base Java file monitoring facilities can only have on key per directoiry
 * path, so multiple clients cannot monitor the same directory. This class
 * allows that to happen.
 */
public class Monitor implements Closeable {

	@FunctionalInterface
	public interface OnChange {
		void onChange(WatchEvent<Path> evt);
	}

	@FunctionalInterface
	public interface MonitorHandle extends Closeable {
		@Override
		void close();
	}

	private final static class Wrapped {
		private OnChange callback;
		private Path dir;
		Wrapped(OnChange callback, Path dir) {
			super();
			this.callback = callback;
			this.dir = dir;
		}
	}

	private final static class Waiting {
		private ScheduledFuture<?> task;
		private Kind<?> kind;
		
		Waiting(ScheduledFuture<?> task, Kind<?> kind) {
			super();
			this.task = task;
			this.kind = kind;
		}
	}

	private final WatchService watchService;
	private final Map<WatchKey, List<Wrapped>> callbacks = Collections.synchronizedMap(new HashMap<>());
	private final Map<Path, WatchKey> pathToWatchKey = Collections.synchronizedMap(new HashMap<>());
	private final Map<String, Waiting> pending = Collections.synchronizedMap(new HashMap<>());
	
	private final boolean shutdownExecutor;
	private final ScheduledExecutorService executor;

	public Monitor() {
		this(Executors.newSingleThreadScheduledExecutor(), true);
	}

	public Monitor(ScheduledExecutorService executor) {
		this(executor, false);
	}

	@SuppressWarnings("unchecked")
	private Monitor(ScheduledExecutorService executor, boolean shutdownExecutor) {
		this.shutdownExecutor = shutdownExecutor;
		this.executor = executor;
		try {
			watchService = FileSystems.getDefault().newWatchService();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		var thr = new Thread(() -> {
			while (true) {
				// wait for key to be signaled
				WatchKey key;
				try {
					key = watchService.take();
				} catch (InterruptedException x) {
					return;
				}

				synchronized (pending) {

					var onChange = callbacks.get(key);

					for (var event : key.pollEvents()) {
						var kind = event.kind();
						if (kind == StandardWatchEventKinds.OVERFLOW) {
							continue;
						}

						var path = (Path) event.context();
						var full = onChange.get(0).dir.resolve(path);
						var fullStr = full.toString();
						var pnd = pending.get(fullStr);

						if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
							if (pnd != null) {
								pnd.task.cancel(false);
							}
							pending.put(fullStr, new Waiting(executor.schedule(() -> {
								synchronized (pending) {
									onChange.forEach(w -> w.callback.onChange((WatchEvent<Path>) event));
									pending.remove(fullStr);
								}
							}, 1, TimeUnit.MILLISECONDS), kind));
						} else if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
							if (pnd != null) {
								pnd.task.cancel(false);
							}
							pending.put(fullStr, new Waiting(executor.schedule(() -> {
								synchronized (pending) {
									onChange.forEach(w -> w.callback.onChange((WatchEvent<Path>) event));
									pending.remove(fullStr);
								}
							}, 1, TimeUnit.SECONDS), kind));
						} else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
							if ((pnd != null && pnd.kind == StandardWatchEventKinds.ENTRY_MODIFY) || pnd == null) {
								if (pnd != null) {
									pnd.task.cancel(false);
								}
								pending.put(fullStr, new Waiting(executor.schedule(() -> {
									synchronized (pending) {
										onChange.forEach(w -> w.callback.onChange((WatchEvent<Path>) event));
										pending.remove(fullStr);
									}
								}, 1, TimeUnit.SECONDS), kind));
							} /*
								 * else if (pnd != null && pnd.kind != StandardWatchEventKinds.ENTRY_MODIFY) {
								 * System.out.println("MOD2"); pnd.task.cancel(false); }
								 */
						}
					}

				}

				key.reset();
			}
		}, "FileMonitor");
		thr.setDaemon(true);
		thr.start();
	}

	public MonitorHandle monitor(Path path, OnChange cb) {
		try {
			synchronized (pending) {
				var key = pathToWatchKey.get(path);
				if (key == null) {
					key = path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
							StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
					pathToWatchKey.put(path, key);
				}
				var l = callbacks.get(key);
				if (l == null) {
					l = new ArrayList<>();
					callbacks.put(key, l);
				}
				var wrapped = new Wrapped(cb, path);
				l.add(wrapped);
				var fl = l;
				var fk = key;
				return new MonitorHandle() {
					@Override
					public void close() {
						synchronized (pending) {
							fl.remove(wrapped);
							if (fl.isEmpty()) {
								callbacks.remove(fk);
								pathToWatchKey.remove(path);
							}
						}
					}
				};
			}
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	@Override
	public void close() throws IOException {
		if(shutdownExecutor) {
			executor.shutdown();
		}
		
	}
}
