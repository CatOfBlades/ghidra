/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.app.plugin.core.debug;

import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

import org.jdom.Element;

import ghidra.app.services.DebuggerTraceManagerService;
import ghidra.app.services.TraceRecorder;
import ghidra.framework.data.ProjectFileManager;
import ghidra.framework.model.*;
import ghidra.framework.options.SaveState;
import ghidra.framework.plugintool.PluginTool;
import ghidra.trace.database.DBTraceContentHandler;
import ghidra.trace.database.DBTraceUtils;
import ghidra.trace.model.Trace;
import ghidra.trace.model.program.TraceProgramView;
import ghidra.trace.model.stack.TraceObjectStackFrame;
import ghidra.trace.model.target.TraceObject;
import ghidra.trace.model.target.TraceObjectKeyPath;
import ghidra.trace.model.thread.TraceObjectThread;
import ghidra.trace.model.thread.TraceThread;
import ghidra.trace.model.time.TraceSnapshot;
import ghidra.trace.model.time.schedule.TraceSchedule;
import ghidra.trace.util.DefaultTraceTimeViewport;
import ghidra.trace.util.TraceTimeViewport;
import ghidra.util.Msg;
import ghidra.util.NotOwnerException;

public class DebuggerCoordinates {

	public static final DebuggerCoordinates NOWHERE =
		new DebuggerCoordinates(null, null, null, null, TraceSchedule.ZERO, 0, null);

	private static final String KEY_TRACE_PROJ_LOC = "TraceProjLoc";
	private static final String KEY_TRACE_PROJ_NAME = "TraceProjName";
	private static final String KEY_TRACE_PATH = "TracePath";
	private static final String KEY_TRACE_VERSION = "TraceVersion";
	private static final String KEY_THREAD_KEY = "ThreadKey";
	private static final String KEY_TIME = "Time";
	private static final String KEY_FRAME = "Frame";
	private static final String KEY_OBJ_PATH = "ObjectPath";

	public static DebuggerCoordinates all(Trace trace, TraceRecorder recorder, TraceThread thread,
			TraceProgramView view, TraceSchedule time, Integer frame, TraceObject object) {
		if (trace == NOWHERE.trace && recorder == NOWHERE.recorder && thread == NOWHERE.thread &&
			view == NOWHERE.view && time == NOWHERE.time && frame == NOWHERE.frame &&
			object == NOWHERE.object) {
			return NOWHERE;
		}
		return new DebuggerCoordinates(trace, recorder, thread, view, time, frame, object);
	}

	public static DebuggerCoordinates trace(Trace trace) {
		if (trace == null) {
			return NOWHERE;
		}
		return all(trace, null, null, null, null, null, null);
	}

	public static DebuggerCoordinates recorder(TraceRecorder recorder) {
		return all(recorder == null ? null : recorder.getTrace(), recorder,
			null, null, recorder == null ? null : TraceSchedule.snap(recorder.getSnap()), null,
			null);
	}

	public static DebuggerCoordinates thread(TraceThread thread) {
		return all(thread == null ? null : thread.getTrace(), null, thread, null, null, null, null);
	}

	public static DebuggerCoordinates rawView(TraceProgramView view) {
		return all(view.getTrace(), null, null, view, TraceSchedule.snap(view.getSnap()), null,
			null);
	}

	public static DebuggerCoordinates view(TraceProgramView view) {
		if (view == null) {
			return NOWHERE;
		}
		long snap = view.getSnap();
		if (!DBTraceUtils.isScratch(snap)) {
			return rawView(view);
		}
		Trace trace = view.getTrace();
		TraceSnapshot snapshot = trace.getTimeManager().getSnapshot(snap, false);
		if (snapshot == null) {
			return rawView(view);
		}
		TraceSchedule schedule = snapshot.getSchedule();
		if (schedule == null) {
			return rawView(view);
		}
		return trace(trace).withTime(schedule);
	}

	public static DebuggerCoordinates snap(long snap) {
		return all(null, null, null, null, TraceSchedule.snap(snap), null, null);
	}

	public static DebuggerCoordinates time(String time) {
		return time(TraceSchedule.parse(time));
	}

	public static DebuggerCoordinates time(TraceSchedule time) {
		return all(null, null, null, null, time, null, null);
	}

	public static DebuggerCoordinates frame(int frame) {
		return all(null, null, null, null, null, frame, null);
	}

	public static DebuggerCoordinates object(TraceObject object) {
		if (object == null) {
			return NOWHERE;
		}
		TraceObjectThread thread = object.queryCanonicalAncestorsInterface(TraceObjectThread.class)
				.findFirst()
				.orElse(null);
		TraceObjectStackFrame frame =
			object.queryCanonicalAncestorsInterface(TraceObjectStackFrame.class)
					.findFirst()
					.orElse(null);
		return all(object.getTrace(), null, thread, null, null,
			frame == null ? null : frame.getLevel(), object);
	}

	public static DebuggerCoordinates threadSnap(TraceThread thread, long snap) {
		return all(thread == null ? null : thread.getTrace(), null, thread, null,
			TraceSchedule.snap(snap), null, null);
	}

	public static boolean equalsIgnoreRecorderAndView(DebuggerCoordinates a,
			DebuggerCoordinates b) {
		if (!Objects.equals(a.trace, b.trace)) {
			return false;
		}
		if (!Objects.equals(a.thread, b.thread)) {
			return false;
		}
		if (!Objects.equals(a.time, b.time)) {
			return false;
		}
		if (!Objects.equals(a.frame, b.frame)) {
			return false;
		}
		return true;
	}

	private final Trace trace;
	private final TraceRecorder recorder;
	private final TraceThread thread;
	private final TraceProgramView view;
	private final TraceSchedule time;
	private final Integer frame;
	private final TraceObject object;

	private final int hash;

	private Long viewSnap;
	private DefaultTraceTimeViewport viewport;

	protected DebuggerCoordinates(Trace trace, TraceRecorder recorder, TraceThread thread,
			TraceProgramView view, TraceSchedule time, Integer frame, TraceObject object) {
		this.trace = trace;
		this.recorder = recorder;
		this.thread = thread;
		this.view = view;
		this.time = time;
		this.frame = frame;
		this.object = object;

		this.hash = Objects.hash(trace, recorder, thread, view, time, frame, object);
	}

	@Override
	public String toString() {
		return String.format(
			"Coords(trace=%s,recorder=%s,thread=%s,view=%s,time=%s,frame=%d,object=%d)",
			trace, recorder, thread, view, time, frame, object);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof DebuggerCoordinates)) {
			return false;
		}
		DebuggerCoordinates that = (DebuggerCoordinates) obj;
		if (!Objects.equals(this.trace, that.trace)) {
			return false;
		}
		if (!Objects.equals(this.recorder, that.recorder)) {
			return false;
		}
		if (!Objects.equals(this.thread, that.thread)) {
			return false;
		}
		if (!Objects.equals(this.view, that.view)) {
			return false;
		}
		if (!Objects.equals(this.time, that.time)) {
			return false;
		}
		if (!Objects.equals(this.frame, that.frame)) {
			return false;
		}
		if (!Objects.equals(this.object, that.object)) {
			return false;
		}
		
		return true;
	}

	@Override
	public int hashCode() {
		return hash;
	}

	public Trace getTrace() {
		return trace;
	}

	public TraceRecorder getRecorder() {
		return recorder;
	}

	public DebuggerCoordinates withRecorder(TraceRecorder newRecorder) {
		return all(trace, newRecorder, thread, view, time, frame, object);
	}

	public TraceThread getThread() {
		return thread;
	}

	public DebuggerCoordinates withReFoundThread() {
		if (trace == null || thread == null) {
			return this;
		}
		TraceThread newThread = trace.getThreadManager().getThread(thread.getKey());
		if (thread == newThread) {
			return this;
		}
		return withThread(newThread);
	}

	public DebuggerCoordinates withThread(TraceThread newThread) {
		return all(trace, recorder, newThread, view, time, frame, object);
	}

	public TraceProgramView getView() {
		return view;
	}

	public Long getSnap() {
		return time.getSnap();
	}

	/**
	 * Get these same coordinates with time replaced by the given snap-only coordinate
	 * 
	 * @param newSnap the new snap
	 * @return the new coordinates
	 */
	public DebuggerCoordinates withSnap(Long newSnap) {
		return all(trace, recorder, thread, view,
			newSnap == null ? time : TraceSchedule.snap(newSnap), frame, object);
	}

	public DebuggerCoordinates withTime(TraceSchedule newTime) {
		return all(trace, recorder, thread, view, newTime, frame, object);
	}

	public DebuggerCoordinates withView(TraceProgramView newView) {
		return all(trace, recorder, thread, newView, time, frame, object);
	}

	public DebuggerCoordinates withObject(TraceObject newObject) {
		return all(trace, recorder, thread, view, time, frame, newObject);
	}

	public TraceSchedule getTime() {
		return time;
	}

	public Integer getFrame() {
		return frame;
	}

	public TraceObject getObject() {
		return object;
	}

	public synchronized long getViewSnap() {
		if (viewSnap != null) {
			return viewSnap;
		}
		if (time.isSnapOnly()) {
			return viewSnap = time.getSnap();
		}
		Collection<? extends TraceSnapshot> snapshots =
			trace.getTimeManager().getSnapshotsWithSchedule(time);
		if (snapshots.isEmpty()) {
			Msg.warn(this,
				"Seems the emulation service did not create the requested snapshot, yet");
			// NB. Don't cache viewSnap. Maybe next time, we'll get it.
			return time.getSnap();
		}
		return viewSnap = snapshots.iterator().next().getKey();
	}

	public synchronized TraceTimeViewport getViewport() {
		if (viewport != null) {
			return viewport;
		}
		if (trace == null) {
			return null;
		}
		viewport = new DefaultTraceTimeViewport(trace);
		viewport.setSnap(getViewSnap());
		return viewport;
	}

	public void writeDataState(PluginTool tool, SaveState saveState, String key) {
		if (this == NOWHERE) {
			return;
		}
		SaveState coordState = new SaveState();
		// for NOWHERE, key should be completely omitted
		if (trace != null) {
			DomainFile df = trace.getDomainFile();
			if (df.getParent() == null) {
				return; // not contained within any project
			}
			ProjectLocator projLoc = df.getProjectLocator();
			if (projLoc != null && !projLoc.isTransient()) {
				coordState.putString(KEY_TRACE_PROJ_LOC, projLoc.getLocation());
				coordState.putString(KEY_TRACE_PROJ_NAME, projLoc.getName());
				coordState.putString(KEY_TRACE_PATH, df.getPathname());
				if (!df.isLatestVersion()) {
					coordState.putInt(KEY_TRACE_VERSION, df.getVersion());
				}
			}
		}
		if (thread != null) {
			coordState.putLong(KEY_THREAD_KEY, thread.getKey());
		}
		if (time != null) {
			coordState.putString(KEY_TIME, time.toString());
		}
		if (frame != null) {
			coordState.putInt(KEY_FRAME, frame);
		}

		saveState.putXmlElement(key, coordState.saveToXml());
	}

	protected static DomainFile getDomainFile(PluginTool tool, SaveState coordState) {
		String pathname = coordState.getString(KEY_TRACE_PATH, null);
		String location = coordState.getString(KEY_TRACE_PROJ_LOC, null);
		String projName = coordState.getString(KEY_TRACE_PROJ_NAME, null);
		if (location == null || projName == null) {
			return null;
		}
		ProjectLocator projLoc = new ProjectLocator(location, projName);

		ProjectData projData = tool.getProject().getProjectData(projLoc);
		if (projData == null) {
			try {
				projData = new ProjectFileManager(projLoc, false, false);
			}
			catch (NotOwnerException e) {
				Msg.showError(DebuggerCoordinates.class, tool.getToolFrame(), "Trace Open Failed",
					"Not project owner: " + projLoc + "(" + pathname + ")");
				return null;
			}
			catch (IOException e) {
				Msg.showError(DebuggerCoordinates.class, tool.getToolFrame(), "Trace Open Failed",
					"Project error: " + e.getMessage());
				return null;
			}
		}

		DomainFile df = projData.getFile(pathname);
		if (df == null || !DBTraceContentHandler.TRACE_CONTENT_TYPE.equals(df.getContentType())) {
			String message = "Can't open trace - \"" + pathname + "\"";
			int version = coordState.getInt(KEY_TRACE_VERSION, DomainFile.DEFAULT_VERSION);
			if (version != DomainFile.DEFAULT_VERSION) {
				message += " version " + version;
			}
			String title = df == null ? "Trace Not Found" : "Wrong File Type";
			Msg.showError(DebuggerCoordinates.class, tool.getToolFrame(), title, message);
			return null;
		}
		return df;
	}

	public static DebuggerCoordinates readDataState(PluginTool tool, SaveState saveState,
			String key, boolean resolve) {
		if (!saveState.hasValue(key)) {
			return NOWHERE;
		}
		DebuggerTraceManagerService traceManager =
			tool.getService(DebuggerTraceManagerService.class);
		Trace trace = null;
		Element coordElement = saveState.getXmlElement(key);
		SaveState coordState = new SaveState(coordElement);
		if (traceManager != null) {
			DomainFile df = getDomainFile(tool, coordState);
			int version = coordState.getInt(KEY_TRACE_VERSION, DomainFile.DEFAULT_VERSION);
			if (df != null) {
				trace = traceManager.openTrace(df, version);
			}
		}
		TraceThread thread = null;
		if (trace != null && coordState.hasValue(KEY_THREAD_KEY)) {
			long threadKey = coordState.getLong(KEY_THREAD_KEY, 0);
			thread = trace.getThreadManager().getThread(threadKey);
		}
		String timeSpec = coordState.getString(KEY_TIME, null);
		TraceSchedule time;
		try {
			time = TraceSchedule.parse(timeSpec);
		}
		catch (Exception e) {
			Msg.error(DebuggerCoordinates.class,
				"Could not restore invalid time specification: " + timeSpec);
			time = TraceSchedule.ZERO;
		}
		Integer frame = null;
		if (coordState.hasValue(KEY_FRAME)) {
			frame = coordState.getInt(KEY_FRAME, 0);
		}
		TraceObject object = null;
		if (trace != null && coordState.hasValue(KEY_OBJ_PATH)) {
			String pathString = coordState.getString(KEY_OBJ_PATH, "");
			try {
				TraceObjectKeyPath path = TraceObjectKeyPath.parse(pathString);
				object = trace.getObjectManager().getObjectByCanonicalPath(path);
			}
			catch (Exception e) {
				Msg.error(DebuggerCoordinates.class, "Could not restore object: " + pathString, e);
				object = trace.getObjectManager().getRootObject();
			}
		}

		DebuggerCoordinates coords =
			DebuggerCoordinates.all(trace, null, thread, null, time, frame, object);
		if (!resolve) {
			return coords;
		}
		return traceManager.resolveCoordinates(coords);
	}

	public boolean isAlive() {
		return recorder != null;
	}

	public boolean isPresent() {
		return recorder.getSnap() == time.getSnap() && time.isSnapOnly();
	}

	public boolean isReadsPresent() {
		return recorder.getSnap() == time.getSnap();
	}

	public boolean isAliveAndPresent() {
		return isAlive() && isPresent();
	}

	public boolean isDeadOrPresent() {
		return !isAlive() || isPresent();
	}

	public boolean isAliveAndReadsPresent() {
		return isAlive() && isReadsPresent();
	}
}
