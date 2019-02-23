/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia;

import javax.media.*;

import org.atalk.util.Logger;

import timber.log.Timber;

/**
 * A utility class that provides utility functions when working with processors.
 *
 * @author Emil Ivov
 * @author Ken Larson
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class ProcessorUtility implements ControllerListener
{
	/**
	 * The <tt>Object</tt> used for syncing when waiting for a processor to enter a specific state.
	 */
	private final Object stateLock = new Object();

	/**
	 * The indicator which determines whether the waiting of this instance on a processor for it to
	 * enter a specific state has failed.
	 */
	private boolean failed = false;

	/**
	 * Initializes a new <tt>ProcessorUtility</tt> instance.
	 */
	public ProcessorUtility()
	{
	}

	/**
	 * Gets the <tt>Object</tt> to use for syncing when waiting for a processor to enter a specific
	 * state.
	 *
	 * @return the <tt>Object</tt> to use for syncing when waiting for a processor to enter a
	 *         specific state
	 */
	private Object getStateLock()
	{
		return stateLock;
	}

	/**
	 * Specifies whether the wait operation has failed or completed with success.
	 *
	 * @param failed
	 *        <tt>true</tt> if waiting has failed; <tt>false</tt>, otherwise
	 */
	private void setFailed(boolean failed)
	{
		this.failed = failed;
	}

	/**
	 * This method is called when an event is generated by a <code>Controller</code> that this
	 * listener is registered with. We use the event to notify all waiting on our lock and record
	 * success or failure.
	 *
	 * @param ce
	 *        The event generated.
	 */
	public void controllerUpdate(ControllerEvent ce)
	{
		// If there was an error during configure or
		// realize, the processor will be closed
		if (ce instanceof ControllerClosedEvent) {
			if (ce instanceof ControllerErrorEvent)
				Timber.w("ControllerErrorEvent: %s", ce);
			else
				Timber.d("ControllerClosedEvent: %s", ce);

			setFailed(true);

			// All controller events, send a notification
			// to the waiting thread in waitForState method.
		}

		Object stateLock = getStateLock();

		synchronized (stateLock) {
			stateLock.notifyAll();
		}
	}

	/**
	 * Waits until <tt>processor</tt> enters state and returns a boolean indicating success or
	 * failure of the operation.
	 *
	 * @param processor
	 *        Processor
	 * @param state
	 *        one of the Processor.XXXed state vars
	 * @return <tt>true</tt> if the state has been reached; <tt>false</tt>, otherwise
	 */
	public synchronized boolean waitForState(Processor processor, int state)
	{
		processor.addControllerListener(this);
		setFailed(false);

		// Call the required method on the processor
		if (state == Processor.Configured)
			processor.configure();
		else if (state == Processor.Realized)
			processor.realize();

		boolean interrupted = false;

		// Wait until we get an event that confirms the
		// success of the method, or a failure event.
		// See StateListener inner class
		while ((processor.getState() < state) && !failed) {
			Object stateLock = getStateLock();

			synchronized (stateLock) {
				try {
					stateLock.wait();
				}
				catch (InterruptedException ie) {
					Timber.w(ie, "Interrupted while waiting on Processor %s for state %s", processor, state);
					/*
					 * XXX It is not really clear what we should do. It seems that an
					 * InterruptedException may be thrown and the Processor will still work fine.
					 * Consequently, we cannot fail here. Besides, if the Processor fails, it will
					 * tell us with a ControllerEvent anyway and we will get out of the loop.
					 */
					interrupted = true;
					// processor.removeControllerListener(this);
					// return false;
				}
			}
		}
		if (interrupted)
			Thread.currentThread().interrupt();

		processor.removeControllerListener(this);
		return !failed;
	}
}
