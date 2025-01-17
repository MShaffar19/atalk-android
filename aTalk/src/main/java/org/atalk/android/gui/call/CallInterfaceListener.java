/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.android.gui.call;

/**
 * The <code>CallInterfaceListener</code> is notified when the call interface has
 * been started after the call was created.
 *
 * @author Yana Stamcheva
 */
public interface CallInterfaceListener
{
    /**
     * Indicates that the call interface was started.
     */
    public void callInterfaceStarted();
}
