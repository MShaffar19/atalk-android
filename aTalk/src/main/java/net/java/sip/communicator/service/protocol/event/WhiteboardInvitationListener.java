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
 * A listener that dispatches events notifying that an invitation to join a white board is received.
 *
 * @author Yana Stamcheva
 */
public interface WhiteboardInvitationListener
{
	/**
	 * Called when we receive an invitation to join an existing <code>WhiteboardSession</code>.
	 * <p>
	 * 
	 * @param evt
	 *        the <code>WhiteboardInvitationReceivedEvent</code> that contains the newly received
	 *        invitation and its source provider.
	 */
	public abstract void invitationReceived(WhiteboardInvitationReceivedEvent evt);

}
