/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.AbstractFileTransfer;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.jingle.component.JingleSessionImpl;
import org.jivesoftware.smackx.jingle.element.JingleReason;
import org.jivesoftware.smackx.jingle_filetransfer.controller.OutgoingFileOfferController;
import org.jivesoftware.smackx.jingle_filetransfer.listener.ProgressListener;

import java.io.File;

import timber.log.Timber;

/**
 * Jabber implementation of the jingle incoming file offer
 *
 * @author Eng Chong Meng
 */

public class OutgoingFileOfferJingleImpl extends AbstractFileTransfer
        implements JingleSessionImpl.JingleSessionListener, ProgressListener
{

    /**
     * Default number of fallback to use HttpFileUpload if previously has securityError
     */
    private static final int defaultErrorTimer = 10;

    /**
     * Fallback to use HttpFileUpload file transfer if previously has securityError i.e. not zero
     */
    private static int securityErrorTimber = 0;

    private final String id;
    private final Contact recipient;
    private final File file;
    private int byteWrite;

    /**
     * The Jingle outgoing file offer.
     */
    private final OutgoingFileOfferController mOfoJingle;
    private final XMPPConnection mConnection;

    /**
     * Creates an <code>OutgoingFileTransferJabberImpl</code> by specifying the <code>receiver</code>
     * contact, the <code>file</code> , the <code>jabberTransfer</code>, that would be used to send the file
     * through Jabber and the <code>protocolProvider</code>.
     *
     * @param recipient the destination contact
     * @param file the file to send
     * @param jabberTransfer the Jabber transfer object, containing all transfer information
     * @param protocolProvider the parent protocol provider
     * @param msgUuid the id that uniquely identifies this file transfer and saved DB record
     */
    public OutgoingFileOfferJingleImpl(Contact recipient, File file, String msgUuid, OutgoingFileOfferController offer,
            XMPPConnection connection)
    {
        this.recipient = recipient;
        this.file = file;
        this.id = msgUuid;
        this.mOfoJingle = offer;
        this.mConnection = connection;
        offer.addProgressListener(this);
        JingleSessionImpl.addJingleSessionListener(this);
        // Timber.d("Add Ofo Listener");
    }

    /**
     * Cancel the file transfer.
     */
    @Override
    public void cancel()
    {
        try {
            onCanceled();
            mOfoJingle.cancel(mConnection);
            removeOfoListener();
        } catch (SmackException.NotConnectedException | InterruptedException | XMPPException.XMPPErrorException
                | SmackException.NoResponseException e) {
            Timber.e("File send cancel exception: %s", e.getMessage());
        }
    }

    private void onCanceled()
    {
        String reason = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_CANCELED);
        fireStatusChangeEvent(FileTransferStatusChangeEvent.CANCELED, reason);
    }

    /**
     * Remove OFO Listener:
     * a. When sender cancel file transfer (FileTransferConversation); nothing returns from remote.
     * b. onSessionTerminated() received from remote (uer declines or cancels during active transfer)
     */
    private void removeOfoListener() {
        // Timber.d("Remove Ofo Listener");
        mOfoJingle.removeProgressListener(this);
        JingleSessionImpl.removeJingleSessionListener(this);
    }

    /**
     * Returns the number of bytes already sent to the recipient.
     *
     * @return the number of bytes already sent to the recipient.
     */
    @Override
    public long getTransferredBytes()
    {
        return byteWrite;
    }

    /**
     * The direction is outgoing.
     *
     * @return OUT.
     */
    public int getDirection()
    {
        return OUT;
    }

    /**
     * Returns the local file that is being transferred or to which we transfer.
     *
     * @return the file
     */
    public File getLocalFile()
    {
        return file;
    }

    /**
     * The contact we are sending the file.
     *
     * @return the receiver.
     */
    public Contact getContact()
    {
        return recipient;
    }

    /**
     * The unique id that uniquely identity the record and in DB.
     *
     * @return the id.
     */
    public String getID()
    {
        return id;
    }

    @Override
    public void onStarted()
    {
        fireStatusChangeEvent(FileTransferStatusChangeEvent.IN_PROGRESS, "InProgress");
    }

    @Override
    public void progress(int rwBytes)
    {
        byteWrite = rwBytes;
        // Timber.d("get TransferredBytes send: %s", byteWrite);
    }

    @Override
    public void onFinished()
    {
        fireStatusChangeEvent(FileTransferStatusChangeEvent.COMPLETED, "Byte sent completed");
    }

    @Override
    public void onError(JingleReason reason)
    {
        onSessionTerminated(reason);
    }

    @Override
    public void onSessionTerminated(JingleReason reason)
    {
        if (JingleReason.Reason.security_error.equals(reason.asEnum())) {
            securityErrorTimber = defaultErrorTimer;
        }
        fireStatusChangeEvent(reason);
        removeOfoListener();
    }

    @Override
    public void onSessionAccepted()
    {
        fireStatusChangeEvent(FileTransferStatusChangeEvent.PREPARING, "negotiating");
    }

    /**
     * Avoid use of Jet for file transfer if it is still within the securityErrorTimber count.
     *
     * @return true if the timer is not zero.
     */
    public static boolean hasSecurityError() {
        return (securityErrorTimber-- > 0);
    }
}
