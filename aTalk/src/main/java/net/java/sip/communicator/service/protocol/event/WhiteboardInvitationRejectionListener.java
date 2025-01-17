/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol.event;

/**
 * A listener that dispatches events notifying that an invitation which was sent earlier has been
 * rejected by the invitee.
 *
 * @author Yana Stamcheva
 */
public interface WhiteboardInvitationRejectionListener
{
	/**
	 * Called when an invitee rejects an invitation previously sent by us.
	 *
	 * @param evt
	 *        the instance of the <code>WhiteboardInvitationRejectedEvent</code> containing the rejected
	 *        white-board invitation as well as the source provider where this happened.
	 */
	public void invitationRejected(WhiteboardInvitationRejectedEvent evt);
}
