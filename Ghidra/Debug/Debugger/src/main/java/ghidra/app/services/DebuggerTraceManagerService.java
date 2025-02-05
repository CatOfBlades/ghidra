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
package ghidra.app.services;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import ghidra.app.plugin.core.debug.DebuggerCoordinates;
import ghidra.app.plugin.core.debug.service.tracemgr.DebuggerTraceManagerServicePlugin;
import ghidra.async.AsyncReference;
import ghidra.framework.model.DomainFile;
import ghidra.framework.plugintool.ServiceInfo;
import ghidra.program.model.listing.Program;
import ghidra.trace.model.Trace;
import ghidra.trace.model.program.TraceProgramView;
import ghidra.trace.model.target.TraceObject;
import ghidra.trace.model.thread.TraceThread;
import ghidra.trace.model.time.schedule.TraceSchedule;
import ghidra.util.TriConsumer;

/**
 * The interface for managing open traces and navigating among them and their contents
 */
@ServiceInfo(defaultProvider = DebuggerTraceManagerServicePlugin.class)
public interface DebuggerTraceManagerService {

	/**
	 * An adapter that works nicely with an {@link AsyncReference}
	 * 
	 * <p>
	 * TODO: Seems this is still leaking an implementation detail
	 */
	public interface BooleanChangeAdapter extends TriConsumer<Boolean, Boolean, Void> {
		@Override
		default void accept(Boolean oldVal, Boolean newVal, Void cause) {
			changed(newVal);
		}

		/**
		 * The value has changed
		 * 
		 * @param value the new value
		 */
		void changed(Boolean value);
	}

	/**
	 * Get all the open traces
	 * 
	 * @return all open traces
	 */
	Collection<Trace> getOpenTraces();

	/**
	 * Get the current coordinates
	 * 
	 * <p>
	 * This entails everything except the current address
	 * 
	 * @return the current coordinates
	 */
	DebuggerCoordinates getCurrent();

	/**
	 * Get the current coordinates for a given trace
	 * 
	 * @param trace the trace
	 * @return the current coordinates for the trace
	 */
	DebuggerCoordinates getCurrentFor(Trace trace);

	/**
	 * Get the active trace
	 * 
	 * @return the active trace, or null
	 */
	Trace getCurrentTrace();

	/**
	 * Get the active view
	 * 
	 * <p>
	 * Every trace has an associated variable-snap view. When the manager navigates to a new point
	 * in time, it is accomplished by changing the snap of this view. This view is suitable for use
	 * in most places where a {@link Program} is ordinarily required.
	 * 
	 * @return the active view, or null
	 */
	TraceProgramView getCurrentView();

	/**
	 * Get the active thread
	 * 
	 * <p>
	 * It is possible to have an active trace, but no active thread.
	 * 
	 * @return the active thread, or null
	 */
	TraceThread getCurrentThread();

	/**
	 * Get the active thread for a given trace
	 * 
	 * <p>
	 * The manager remembers the last active thread for every open trace. If the trace has never
	 * been active, then the last active thread is null. If trace is the active trace, then this
	 * will return the currently active thread.
	 * 
	 * @param trace the trace
	 * @return the thread, or null
	 */
	TraceThread getCurrentThreadFor(Trace trace);

	/**
	 * Get the active snap
	 * 
	 * <p>
	 * Note that if emulation was used to materialize the current coordinates, then the current snap
	 * will differ from the view's snap.
	 * 
	 * @return the active snap, or 0
	 */
	long getCurrentSnap();

	/**
	 * Get the active frame
	 * 
	 * @return the active frame, or 0
	 */
	int getCurrentFrame();

	/**
	 * Get the active object
	 * 
	 * @return the active object, or null
	 */
	TraceObject getCurrentObject();

	/**
	 * Open a trace
	 * 
	 * <p>
	 * This does not activate the trace. Use {@link #activateTrace(Trace)} or
	 * {@link #activateThread(TraceThread)} if necessary.
	 * 
	 * @param trace the trace to open
	 */
	void openTrace(Trace trace);

	/**
	 * Open a trace from a domain file
	 * 
	 * @param file the domain file to open
	 * @param version the version (read-only if non-default)
	 * @return the trace
	 * @throws ClassCastException if the domain object contains a non-trace object
	 */
	Trace openTrace(DomainFile file, int version);

	/**
	 * Open traces from a collection of domain files
	 * 
	 * <p>
	 * Iterating the returned trace collection orders each trace by position of its file in the
	 * input file collection.
	 * 
	 * @param files the domain files
	 * @return the traces opened
	 */
	Collection<Trace> openTraces(Collection<DomainFile> files);

	/**
	 * Save the trace to the "New Traces" folder of the project
	 * 
	 * <p>
	 * If a different domain file of the trace's name already exists, an incrementing integer is
	 * appended. Errors are handled in the same fashion as saving a program, so there is little/no
	 * need to invoke {@link CompletableFuture#exceptionally(java.util.function.Function)} on the
	 * returned future. The future is returned as a means of registering follow-up actions.
	 * 
	 * <p>
	 * TODO: Support save-as, prompting to overwrite, etc?
	 * 
	 * @param trace the trace to save
	 * @return a future which completes when the save is finished
	 */
	CompletableFuture<Void> saveTrace(Trace trace);

	/**
	 * Close the given trace
	 * 
	 * @param trace the trace to close
	 */
	void closeTrace(Trace trace);

	/**
	 * Close all traces
	 */
	void closeAllTraces();

	/**
	 * Close all traces except the given one
	 * 
	 * @param keep the trace to keep open
	 */
	void closeOtherTraces(Trace keep);

	/**
	 * Close all traces which are not the destination of a live recording
	 * 
	 * <p>
	 * Operation of this method depends on the model service. If that service is not present, this
	 * method performs no operation at all.
	 */
	void closeDeadTraces();

	/**
	 * Activate the given coordinates with future notification
	 * 
	 * <p>
	 * This operation may be completed asynchronously, esp., if emulation is required to materialize
	 * the coordinates. The returned future is completed when the coordinates are actually
	 * materialized and active. The coordinates are "resolved" as a means of filling in missing
	 * parts. For example, if the thread is not specified, the manager may activate the last-active
	 * thread for the desired trace.
	 * 
	 * @param coordinates the desired coordinates
	 * @param syncTargetFocus true synchronize the current target to the same coordinates
	 * @return a future which completes when emulation and navigation is complete
	 */
	CompletableFuture<Void> activateAndNotify(DebuggerCoordinates coordinates,
			boolean syncTargetFocus);

	/**
	 * Activate the given coordinates, synchronizing the current target, if possible
	 * 
	 * <p>
	 * If asynchronous notification is needed, use
	 * {@link #activateAndNotify(DebuggerCoordinates, boolean)}.
	 * 
	 * @param coordinates the desired coordinates
	 */
	void activate(DebuggerCoordinates coordinates);

	/**
	 * Activate the given trace
	 * 
	 * @param trace the desired trace
	 */
	void activateTrace(Trace trace);

	/**
	 * Activate the given thread
	 * 
	 * @param thread the desired thread
	 */
	void activateThread(TraceThread thread);

	/**
	 * Activate the given snapshot key
	 * 
	 * @param snap the desired snapshot key
	 */
	void activateSnap(long snap);

	/**
	 * Activate the given point in time, possibly invoking emulation
	 * 
	 * @param time the desired schedule
	 */
	void activateTime(TraceSchedule time);

	/**
	 * Activate the given stack frame
	 * 
	 * @param frameLevel the level of the desired frame, 0 being innermost
	 */
	void activateFrame(int frameLevel);

	/**
	 * Activate the given object
	 * 
	 * @param object the desired object
	 */
	void activateObject(TraceObject object);

	/**
	 * Control whether the trace manager automatically activates the "present snapshot"
	 * 
	 * <p>
	 * Auto activation only applies when the current trace advances. It never changes to another
	 * trace.
	 * 
	 * @param enabled true to enable auto activation
	 */
	void setAutoActivatePresent(boolean enabled);

	/**
	 * Check if the trace manager automatically activate the "present snapshot"
	 * 
	 * @return true if auto activation is enabled
	 */
	boolean isAutoActivatePresent();

	/**
	 * Add a listener for changes to auto activation enablement
	 * 
	 * @param listener the listener to receive change notifications
	 */
	void addAutoActivatePresentChangeListener(BooleanChangeAdapter listener);

	/**
	 * Remove a listener for changes to auto activation enablement
	 * 
	 * @param listener the listener receiving change notifications
	 */
	void removeAutoActivatePresentChangeListener(BooleanChangeAdapter listener);

	/**
	 * Control whether trace activation is synchronized with debugger focus/select
	 * 
	 * @param enabled true to synchronize, false otherwise
	 */
	void setSynchronizeFocus(boolean enabled);

	/**
	 * Check whether trace activation is synchronized with debugger focus/select
	 * 
	 * @return true if synchronized, false otherwise
	 */
	boolean isSynchronizeFocus();

	/**
	 * Add a listener for changes to focus synchronization enablement
	 * 
	 * @param listener the listener to receive change notifications
	 */
	void addSynchronizeFocusChangeListener(BooleanChangeAdapter listener);

	/**
	 * Remove a listener for changes to focus synchronization enablement
	 * 
	 * @param listener the listener receiving change notifications
	 */
	void removeSynchronizeFocusChangeListener(BooleanChangeAdapter listener);

	/**
	 * Control whether traces should be saved by default
	 * 
	 * @param enabled true to save by default, false otherwise
	 */
	void setSaveTracesByDefault(boolean enabled);

	/**
	 * Check whether traces should by saved by default
	 * 
	 * @return true if saved by default, false otherwise
	 */
	boolean isSaveTracesByDefault();

	/**
	 * Add a listener for changes to save-by-default enablement
	 * 
	 * @param listener the listener to receive change notifications
	 */
	void addSaveTracesByDefaultChangeListener(BooleanChangeAdapter listener);

	/**
	 * Remove a listener for changes to save-by-default enablement
	 * 
	 * @param listener the listener receiving change notifications
	 */
	void removeSaveTracesByDefaultChangeListener(BooleanChangeAdapter listener);

	/**
	 * Control whether live traces are automatically closed upon target termination
	 * 
	 * @param enabled true to automatically close, false to leave open
	 */
	void setAutoCloseOnTerminate(boolean enabled);

	/**
	 * Check whether live traces are automatically closed upon target termination
	 * 
	 * @return true if automatically closed, false if left open
	 */
	boolean isAutoCloseOnTerminate();

	/**
	 * Add a listener for changes to close-on-terminate enablement
	 * 
	 * @param listener the listener to receive change notifications
	 */
	void addAutoCloseOnTerminateChangeListener(BooleanChangeAdapter listener);

	/**
	 * Remove a listener for changes to close-on-terminate enablement
	 * 
	 * @param listener the listener receiving change notifications
	 */
	void removeAutoCloseOnTerminateChangeListener(BooleanChangeAdapter listener);

	/**
	 * Fill in an incomplete coordinate specification, using the manager's "best judgment"
	 * 
	 * @param coords the possibly-incomplete coordinates
	 * @return the complete resolved coordinates
	 */
	DebuggerCoordinates resolveCoordinates(DebuggerCoordinates coordinates);

	/**
	 * If the given coordinates are already materialized, get the snapshot
	 * 
	 * <p>
	 * If the coordinates do not include a schedule, this simply returns the coordinates' snapshot.
	 * Otherwise, it searches for the first snapshot whose schedule is the coordinates' schedule.
	 * 
	 * @param coordinates the coordinates
	 * @return the materialized snapshot key, or null if not materialized.
	 */
	Long findSnapshot(DebuggerCoordinates coordinates);

	/**
	 * Materialize the given coordinates to a snapshot in the same trace
	 * 
	 * <p>
	 * If the given coordinates do not require emulation, then this must complete immediately with
	 * the snapshot key given by the coordinates. If the given schedule is already materialized in
	 * the trace, then this may complete immediately with the previously-materialized snapshot key.
	 * Otherwise, this must invoke emulation, store the result into a chosen snapshot, and complete
	 * with its key.
	 * 
	 * @param coordinates the coordinates to materialize
	 * @return a future that completes with the snapshot key of the materialized coordinates
	 */
	CompletableFuture<Long> materialize(DebuggerCoordinates coordinates);
}
