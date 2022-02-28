/*
 * Copyright @ 2015 Atlassian Pty Ltd
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
package org.atalk.impl.neomedia;

/**
 * Represents a predicate (boolean-valued function) of a <code>RawPacket</code>.
 *
 * @author George Politis
 */
public class RTCPPacketPredicate extends AbstractRTPPacketPredicate
{
	/**
	 * The singleton instance of this class.
	 */
	public static final RTCPPacketPredicate INSTANCE = new RTCPPacketPredicate();

	/**
	 * Ctor.
	 */
	public RTCPPacketPredicate()
	{
		super(true);
	}
}
