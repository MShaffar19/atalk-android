/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;

import net.java.sip.communicator.impl.protocol.jabber.OperationSetFileTransferJabberImpl;
import net.java.sip.communicator.impl.protocol.jabber.OutgoingFileOfferJingleImpl;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.ContactResource;
import net.java.sip.communicator.service.protocol.IMessage;
import net.java.sip.communicator.service.protocol.OperationNotSupportedException;
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging;
import net.java.sip.communicator.service.protocol.OperationSetChatStateNotifications;
import net.java.sip.communicator.service.protocol.OperationSetContactCapabilities;
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer;
import net.java.sip.communicator.service.protocol.OperationSetMessageCorrection;
import net.java.sip.communicator.service.protocol.OperationSetPresence;
import net.java.sip.communicator.service.protocol.OperationSetSmsMessaging;
import net.java.sip.communicator.service.protocol.OperationSetThumbnailedFileFactory;
import net.java.sip.communicator.service.protocol.PresenceStatus;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.MessageListener;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.filetransfer.FileSendConversation;
import org.atalk.crypto.omemo.OmemoAuthenticateDialog;
import org.atalk.persistance.FileBackend;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.hashes.HashManager;
import org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager;
import org.jivesoftware.smackx.jet.JetManager;
import org.jivesoftware.smackx.jet.component.JetSecurityImpl;
import org.jivesoftware.smackx.jingle_filetransfer.JingleFileTransferManager;
import org.jivesoftware.smackx.jingle_filetransfer.component.JingleFile;
import org.jivesoftware.smackx.jingle_filetransfer.component.JingleFileTransferImpl;
import org.jivesoftware.smackx.jingle_filetransfer.controller.OutgoingFileOfferController;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.omemo.provider.OmemoVAxolotlProvider;
import org.jivesoftware.smackx.omemo.util.OmemoConstants;
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.FullJid;
import org.jxmpp.jid.Jid;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;

import javax.net.ssl.SSLHandshakeException;

import timber.log.Timber;

/**
 * The single chat implementation of the <code>ChatTransport</code> interface that provides abstraction
 * to protocol provider access and its supported features available to the metaContact.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class MetaContactChatTransport implements ChatTransport, ContactPresenceStatusListener
{
    /**
     * The parent <code>ChatSession</code>, where this transport is available.
     */
    private final MetaContactChatSession parentChatSession;

    private final OperationSetFileTransferJabberImpl ftOpSet;

    /**
     * The associated protocol <code>Contact</code>.
     */
    private final Contact contact;

    /**
     * The associated protocol provider service for the <code>Contact</code>.
     */
    private final ProtocolProviderService mPPS;

    private HttpFileUploadManager httpFileUploadManager;
    private JingleFileTransferManager jingleFTManager;
    private JetManager jetManager;

    /**
     * The resource associated with this contact.
     */
    private final ContactResource contactResource;

    /**
     * <code>true</code> when a contact sends a message with XEP-0164 message delivery receipt;
     * override contact disco#info no XEP-0184 feature advertised.
     */
    private boolean isDeliveryReceiptSupported;

    /**
     * <code>true</code> when a contact sends a message with XEP-0085 chat state notifications;
     * override contact disco#info no XEP-0085 feature advertised.
     */
    private static boolean isChatStateSupported = false;

    /**
     * The protocol presence operation set associated with this transport.
     */
    private final OperationSetPresence presenceOpSet;

    /**
     * The thumbnail default width.
     */
    private static final int THUMBNAIL_WIDTH = 64;

    /**
     * The thumbnail default height.
     */
    private static final int THUMBNAIL_HEIGHT = 64;

    /**
     * Indicates if only the resource name should be displayed.
     */
    private final boolean isDisplayResourceOnly;

    /**
     * Creates an instance of <code>MetaContactChatTransport</code> by specifying the parent
     * <code>chatSession</code> and the <code>contact</code> associated with the transport.
     *
     * @param chatSession the parent <code>ChatSession</code>
     * @param contact the <code>Contact</code> associated with this transport
     */
    public MetaContactChatTransport(MetaContactChatSession chatSession, Contact contact)
    {
        this(chatSession, contact, null, false);
    }

    /**
     * Creates an instance of <code>MetaContactChatTransport</code> by specifying the parent
     * <code>chatSession</code>, <code>contact</code>, and the <code>contactResource</code>
     * associated with the transport.
     *
     * @param chatSession the parent <code>ChatSession</code>
     * @param contact the <code>Contact</code> associated with this transport
     * @param contactResource the <code>ContactResource</code> associated with the contact
     * @param isDisplayResourceOnly indicates if only the resource name should be displayed
     */
    public MetaContactChatTransport(MetaContactChatSession chatSession, Contact contact,
            ContactResource contactResource, boolean isDisplayResourceOnly)
    {
        this.parentChatSession = chatSession;
        this.contact = contact;
        this.contactResource = contactResource;
        this.isDisplayResourceOnly = isDisplayResourceOnly;
        mPPS = contact.getProtocolProvider();
        ftOpSet = (OperationSetFileTransferJabberImpl) mPPS.getOperationSet(OperationSetFileTransfer.class);

        presenceOpSet = mPPS.getOperationSet(OperationSetPresence.class);
        if (presenceOpSet != null)
            presenceOpSet.addContactPresenceStatusListener(this);

        isChatStateSupported = (mPPS.getOperationSet(OperationSetChatStateNotifications.class) != null);

        // checking these can be slow so make sure they are run in new thread
        new Thread()
        {
            @Override
            public void run()
            {
                XMPPConnection connection = mPPS.getConnection();
                if ((connection != null)) {
                    // For unencrypted file transfer
                    jingleFTManager = JingleFileTransferManager.getInstanceFor(connection);

                    // For encrypted file transfer using Jet and OMEMO encryption
                    JetManager.registerEnvelopeProvider(OmemoConstants.OMEMO_NAMESPACE_V_AXOLOTL, new OmemoVAxolotlProvider());
                    jetManager = JetManager.getInstanceFor(connection);
                    jetManager.registerEnvelopeManager(OmemoManager.getInstanceFor(connection));

                    // For HttpFileUpload service
                    httpFileUploadManager = HttpFileUploadManager.getInstanceFor(connection);
                    isDeliveryReceiptSupported = checkDeliveryReceiptSupport(connection);
                }
                checkImCaps();
            }
        }.start();
    }

    /**
     * Check for Delivery Receipt support for all registered contacts (ANR from field - so run in thread)
     * Currently isDeliveryReceiptSupported is not used - Smack autoAddDeliveryReceiptRequests support is global
     */
    private boolean checkDeliveryReceiptSupport(XMPPConnection connection)
    {
        boolean isSupported = false;
        Jid fullJid = null;

        // ANR from field - check isAuthenticated() before proceed
        if ((connection != null) && connection.isAuthenticated()) {
            DeliveryReceiptManager deliveryReceiptManager = DeliveryReceiptManager.getInstanceFor(connection);

            List<Presence> presences = Roster.getInstanceFor(connection).getPresences(contact.getJid().asBareJid());
            for (Presence presence : presences) {
                fullJid = presence.getFrom();
                try {
                    if ((fullJid != null) && deliveryReceiptManager.isSupported(fullJid)) {
                        isSupported = true;
                        break;
                    }
                } catch (XMPPException | SmackException | InterruptedException | IllegalArgumentException e) {
                    // AbstractXMPPConnection.createStanzaCollectorAndSend() throws IllegalArgumentException
                    Timber.w("Check Delivery Receipt exception for %s: %s", fullJid, e.getMessage());
                }
            }
            Timber.d("isDeliveryReceiptSupported for: %s = %s", fullJid, isSupported);
        }
        return isSupported;
    }

    /**
     * If sending im is supported check it for supporting html messages if a font is set.
     * As it can be slow make sure its not on our way
     */
    private void checkImCaps()
    {
        if ((ConfigurationUtils.getChatDefaultFontFamily() != null)
                && (ConfigurationUtils.getChatDefaultFontSize() > 0)) {
            OperationSetBasicInstantMessaging imOpSet = mPPS.getOperationSet(OperationSetBasicInstantMessaging.class);

            if (imOpSet != null)
                imOpSet.isContentTypeSupported(IMessage.ENCODE_HTML, contact);
        }
    }

    /**
     * Returns the contact associated with this transport.
     *
     * @return the contact associated with this transport
     */
    public Contact getContact()
    {
        return contact;
    }

    /**
     * Returns the contact address corresponding to this chat transport.
     *
     * @return The contact address corresponding to this chat transport.
     */
    public String getName()
    {
        return contact.getAddress();
    }

    /**
     * Returns the display name corresponding to this chat transport.
     *
     * @return The display name corresponding to this chat transport.
     */
    public String getDisplayName()
    {
        return contact.getDisplayName();
    }

    /**
     * Returns the contact resource of this chat transport that encapsulate
     * contact information of the contact who is logged.
     *
     * @return The display name of this chat transport resource.
     */
    public ContactResource getContactResource()
    {
        return contactResource;
    }

    /**
     * Returns the resource name of this chat transport. This is for example the name of the
     * user agent from which the contact is logged.
     *
     * @return The display name of this chat transport resource.
     */
    public String getResourceName()
    {
        if (contactResource != null)
            return contactResource.getResourceName();
        return null;
    }

    public boolean isDisplayResourceOnly()
    {
        return isDisplayResourceOnly;
    }

    /**
     * Returns the presence status of this transport;
     * with the higher threshold of the two when (contactResource != null)
     *
     * @return the presence status of this transport.
     */
    public PresenceStatus getStatus()
    {
        PresenceStatus contactStatus = contact.getPresenceStatus();
        if (contactResource != null) {
            PresenceStatus resourceStatus = contactResource.getPresenceStatus();
            return (resourceStatus.compareTo(contactStatus) < 0) ? contactStatus : resourceStatus;
        }
        else {
            return contactStatus;
        }
    }

    /**
     * Returns the <code>ProtocolProviderService</code>, corresponding to this chat transport.
     *
     * @return the <code>ProtocolProviderService</code>, corresponding to this chat transport.
     */
    public ProtocolProviderService getProtocolProvider()
    {
        return mPPS;
    }

    /**
     * Returns {@code true} if this chat transport supports instant
     * messaging, otherwise returns {@code false}.
     *
     * @return {@code true} if this chat transport supports instant
     * messaging, otherwise returns {@code false}.
     */
    public boolean allowsInstantMessage()
    {
        // First try to ask the capabilities operation set if such is available.
        OperationSetContactCapabilities capOpSet = mPPS.getOperationSet(OperationSetContactCapabilities.class);
        if (capOpSet != null) {
            if (contact.getJid().asEntityBareJidIfPossible() == null) {
                isChatStateSupported = false;
                return false;
            }
            return capOpSet.getOperationSet(contact, OperationSetBasicInstantMessaging.class) != null;
        }
        else
            return mPPS.getOperationSet(OperationSetBasicInstantMessaging.class) != null;

    }

    /**
     * Returns {@code true} if this chat transport supports message corrections and false otherwise.
     *
     * @return {@code true} if this chat transport supports message corrections and false otherwise.
     */
    public boolean allowsMessageCorrections()
    {
        OperationSetContactCapabilities capOpSet
                = getProtocolProvider().getOperationSet(OperationSetContactCapabilities.class);
        if (capOpSet != null) {
            return capOpSet.getOperationSet(contact, OperationSetMessageCorrection.class) != null;
        }
        else {
            return mPPS.getOperationSet(OperationSetMessageCorrection.class) != null;
        }
    }

    /**
     * Returns {@code true} if this chat transport supports sms
     * messaging, otherwise returns {@code false}.
     *
     * @return {@code true} if this chat transport supports sms
     * messaging, otherwise returns {@code false}.
     */
    public boolean allowsSmsMessage()
    {
        // First try to ask the capabilities operation set if such is available.
        OperationSetContactCapabilities capOpSet
                = getProtocolProvider().getOperationSet(OperationSetContactCapabilities.class);
        if (capOpSet != null) {
            return capOpSet.getOperationSet(contact, OperationSetSmsMessaging.class) != null;
        }
        else
            return mPPS.getOperationSet(OperationSetSmsMessaging.class) != null;
    }

    /**
     * Returns {@code true} if this chat transport supports message delivery receipts,
     * otherwise returns {@code false}.
     * User SHOULD explicitly discover whether the Contact supports the protocol or negotiate the
     * use of message delivery receipt with the Contact (e.g., via XEP-0184 Stanza Session Negotiation).
     *
     * @return {@code true} if this chat transport supports message delivery receipts,
     * otherwise returns {@code false}
     */
    public boolean allowsMessageDeliveryReceipt()
    {
        return isDeliveryReceiptSupported;
    }

    /**
     * Returns {@code true} if this chat transport supports chat state notifications, otherwise returns {@code false}.
     * User SHOULD explicitly discover whether the Contact supports the protocol or negotiate the
     * use of chat state notifications with the Contact (e.g., via XEP-0155 Stanza Session Negotiation).
     *
     * @return {@code true} if this chat transport supports chat state
     * notifications, otherwise returns {@code false}.
     */
    public boolean allowsChatStateNotifications()
    {
        // Object tnOpSet = mPPS.getOperationSet(OperationSetChatStateNotifications.class);
        // return ((tnOpSet != null) && isChatStateSupported);
        return isChatStateSupported;

    }

    public static void setChatStateSupport(boolean isEnable)
    {
        isChatStateSupported = isEnable;
    }

    /**
     * Returns {@code true} if this chat transport supports file transfer, otherwise returns {@code false}.
     *
     * @return {@code true} if this chat transport supports file transfer, otherwise returns {@code false}.
     */
    public boolean allowsFileTransfer()
    {
        return (ftOpSet != null)
                || ((httpFileUploadManager != null) && httpFileUploadManager.isUploadServiceDiscovered());
    }

    /**
     * Sends the given instant message through this chat transport, by specifying the mime type
     * (html or plain text).
     *
     * @param message The message to send.
     * @param encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     */
    public void sendInstantMessage(String message, int encType)
    {
        // If this chat transport does not support instant messaging we do nothing here.
        if (!allowsInstantMessage()) {
            aTalkApp.showToastMessage(R.string.service_gui_SEND_MESSAGE_NOT_SUPPORTED, getName());
            return;
        }

        OperationSetBasicInstantMessaging imOpSet = mPPS.getOperationSet(OperationSetBasicInstantMessaging.class);
        // Strip HTML flag if ENCODE_HTML not supported by the operation
        if (!imOpSet.isContentTypeSupported(IMessage.ENCODE_HTML))
            encType = encType & IMessage.FLAG_MODE_MASK;

        IMessage msg = imOpSet.createMessage(message, encType, "");
        ContactResource toResource = (contactResource != null) ? contactResource : ContactResource.BASE_RESOURCE;
        if (IMessage.ENCRYPTION_OMEMO == (encType & IMessage.ENCRYPTION_MASK)) {
            OmemoManager omemoManager = OmemoManager.getInstanceFor(mPPS.getConnection());
            imOpSet.sendInstantMessage(contact, toResource, msg, null, omemoManager);
        }
        else {
            imOpSet.sendInstantMessage(contact, toResource, msg);
        }
    }

    /**
     * Sends <code>message</code> as a message correction through this transport, specifying the mime
     * type (html or plain text) and the id of the message to replace.
     *
     * @param message The message to send.
     * @param encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     * @param correctedMessageUID The ID of the message being corrected by this message.
     */
    public void sendInstantMessage(String message, int encType, String correctedMessageUID)
    {
        if (!allowsMessageCorrections()) {
            return;
        }

        OperationSetMessageCorrection mcOpSet = mPPS.getOperationSet(OperationSetMessageCorrection.class);
        if (!mcOpSet.isContentTypeSupported(IMessage.ENCODE_HTML))
            encType = encType & IMessage.FLAG_MODE_MASK;
        IMessage msg = mcOpSet.createMessage(message, encType, "");

        ContactResource toResource = (contactResource != null) ? contactResource : ContactResource.BASE_RESOURCE;
        if (IMessage.ENCRYPTION_OMEMO == (encType & IMessage.ENCRYPTION_MASK)) {
            OmemoManager omemoManager = OmemoManager.getInstanceFor(mPPS.getConnection());
            mcOpSet.sendInstantMessage(contact, toResource, msg, correctedMessageUID, omemoManager);
        }
        else {
            mcOpSet.correctMessage(contact, toResource, msg, correctedMessageUID);
        }
    }

    /**
     * Determines whether this chat transport supports the supplied content type
     *
     * @param mimeType the mime type we want to check
     * @return <code>true</code> if the chat transport supports it and <code>false</code> otherwise.
     */
    public boolean isContentTypeSupported(int mimeType)
    {
        OperationSetBasicInstantMessaging imOpSet = mPPS.getOperationSet(OperationSetBasicInstantMessaging.class);
        return (imOpSet != null) && imOpSet.isContentTypeSupported(mimeType);
    }

    /**
     * Sends the given sms message through this chat transport.
     *
     * @param phoneNumber phone number of the destination
     * @param messageText The message to send.
     * @throws Exception if the send operation is interrupted
     */
    public void sendSmsMessage(String phoneNumber, String messageText)
            throws Exception
    {
        // If this chat transport does not support sms messaging we do nothing here.
        if (allowsSmsMessage()) {
            Timber.w("Method not implemented");
            // SMSManager.sendSMS(mPPS, phoneNumber, messageText);}
        }
    }

    /**
     * Whether a dialog need to be opened so the user can enter the destination number.
     *
     * @return <code>true</code> if dialog needs to be open.
     */
    public boolean askForSMSNumber()
    {
        // If this chat transport does not support sms messaging we do nothing here.
        if (!allowsSmsMessage())
            return false;

        OperationSetSmsMessaging smsOpSet = mPPS.getOperationSet(OperationSetSmsMessaging.class);
        return smsOpSet.askForNumber(contact);
    }

    /**
     * Sends the given sms message through this chat transport.
     *
     * @param message the message to send
     * @throws Exception if the send operation is interrupted
     */
    public void sendSmsMessage(String message)
            throws Exception
    {
        // If this chat transport does not support sms messaging we do nothing here.
        if (allowsSmsMessage()) {
            Timber.w("Method not implemented");
            // SMSManager.sendSMS(contact, message);
        }
    }

    /**
     * Sends a chat state notification.
     *
     * @param chatState the chat state notification to send
     */
    public void sendChatStateNotification(ChatState chatState)
    {
        // If this chat transport does not allow chat state notification then just return
        if (allowsChatStateNotifications()) {
            // if protocol is not registered or contact is offline don't try to send chat state notifications
            if (mPPS.isRegistered()
                    && (contact.getPresenceStatus().getStatus() >= PresenceStatus.ONLINE_THRESHOLD)) {

                OperationSetChatStateNotifications tnOperationSet
                        = mPPS.getOperationSet(OperationSetChatStateNotifications.class);
                try {
                    tnOperationSet.sendChatStateNotification(contact, chatState);
                } catch (Exception ex) {
                    Timber.e(ex, "Failed to send chat state notifications.");
                }
            }
        }
    }

    /**
     * Sends the given sticker through this chat transport file transfer operation set.
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon an instance of FileSendConversation
     * @return the <code>FileTransfer</code> or HTTPFileUpload object charged to transfer the given <code>file</code>.
     * @throws Exception if anything goes wrong
     */
    public Object sendSticker(File file, int chatType, FileSendConversation xferCon)
            throws Exception
    {
        // If this chat transport does not support file transfer we do nothing and just return.
        if (!allowsFileTransfer())
            return null;

        if (getStatus().isOnline()) {
            try {
                // Try jingle file protocol as first try if supported by buddy
                return jingleFileSend(file, chatType, xferCon);
            } catch (OperationNotSupportedException ex) {
                // Retry with legacy if jingle file transfer protocols is not supported by buddy;
                // but use http file upload for OMEMO media file sharing
                if (ChatFragment.MSGTYPE_OMEMO == chatType)
                    return httpFileUpload(file, chatType, xferCon);
                else {
                    try {
                        return ftOpSet.sendFile(contact, file, xferCon.getMessageUuid());
                    } catch (OperationNotSupportedException ex2) {
                        // Fallback to use Http file upload if contact is offline, or contact
                        // does not support legacy FileTransfer in SOCKS5 and IBS
                        return httpFileUpload(file, chatType, xferCon);
                    }
                }
            }
        }
        else {
            // Use http file upload for all media file sharing for offline user
            return httpFileUpload(file, chatType, xferCon);
        }
    }

    /**
     * Sends the given SMS multimedia message via this chat transport, leaving the
     * transport to choose the destination.
     *
     * @param file the file to send
     * @throws Exception if the send file is unsuccessful
     */
    public Object sendMultimediaFile(File file)
            throws Exception
    {
        return sendFile(file, true, ChatFragment.MSGTYPE_NORMAL, null);
    }

    /**
     * Sends the given file through this chat transport file transfer operation set.
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon an instance of FileSendConversation
     * @return the <code>FileTransfer</code> or HTTPFileUpload object charged to transfer the given <code>file</code>.
     * @throws Exception if anything goes wrong
     */
    public Object sendFile(File file, int chatType, FileSendConversation xferCon)
            throws Exception
    {
        return sendFile(file, false, chatType, xferCon);
    }

    /**
     * Sends the given file through this chat transport file transfer operation set.
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon an instance of FileSendConversation
     * @return the <code>FileTransfer</code> or HTTPFileUpload object charged to transfer the given <code>file</code>.
     * @throws Exception if anything goes wrong
     */
    private Object sendFile(File file, boolean isMultimediaMessage, int chatType, FileSendConversation xferCon)
            throws Exception
    {
        // If this chat transport does not support file transfer we do nothing and just return.
        if (!allowsFileTransfer())
            return null;

        // Create a thumbNailed file if possible. Skip for OMEMO message
        if (FileBackend.isMediaFile(file) && (ChatFragment.MSGTYPE_OMEMO != chatType)) {
            OperationSetThumbnailedFileFactory tfOpSet = mPPS.getOperationSet(OperationSetThumbnailedFileFactory.class);
            if (tfOpSet != null) {
                byte[] thumbnail = getFileThumbnail(file);
                if (thumbnail != null && thumbnail.length > 0) {
                    file = tfOpSet.createFileWithThumbnail(file, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT,
                            "image/png", thumbnail);
                }
            }
        }

        if (isMultimediaMessage) {
            OperationSetSmsMessaging smsOpSet = mPPS.getOperationSet(OperationSetSmsMessaging.class);
            if (smsOpSet == null)
                return null;
            return smsOpSet.sendMultimediaFile(contact, file);
        }
        else {
            if (getStatus().isOnline()) {
                try {
                    // Try jingle file transfer protocol as first attempt if supported by buddy
                    return jingleFileSend(file, chatType, xferCon);
                } catch (OperationNotSupportedException ex) {
                    // Use http file upload for OMEMO media file sharing;
                    // else use legacy if jingle file transfer protocols is not supported by buddy;
                    if (ChatFragment.MSGTYPE_OMEMO == chatType)
                        return httpFileUpload(file, chatType, xferCon);
                    else {
                        try {
                            return ftOpSet.sendFile(contact, file, xferCon.getMessageUuid());
                        } catch (OperationNotSupportedException ex2) {
                            // Fallback to use Http file upload if contact is offline, or contact
                            // does not support legacy FileTransfer in SOCKS5 and IBS
                            return httpFileUpload(file, chatType, xferCon);
                        }
                    }
                }
            }
            else {
                // Use http file upload for all media file sharing for offline user
                return httpFileUpload(file, chatType, xferCon);
            }
        }
    }

    /**
     * Use Jingle File Transfer or Http file upload that is supported by the transport
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon an instance of FileSendConversation
     * @return <code>OutgoingFileOfferController</code> or HTTPFileUpload object to transfer the given <code>file</code>.
     * @throws Exception if anything goes wrong
     */
    private OutgoingFileOfferJingleImpl jingleFileSend(File file, int chatType, FileSendConversation xferCon)
            throws Exception
    {
        // toJid is not null if contact is online and supports the jet/jingle file transfer
        FullJid recipient;
        if (ChatFragment.MSGTYPE_OMEMO == chatType) {
            recipient = ftOpSet.getFullJid(contact, JetSecurityImpl.NAMESPACE, JingleFileTransferImpl.NAMESPACE);
        }
        else {
            recipient = ftOpSet.getFullJid(contact, JingleFileTransferImpl.NAMESPACE);
        }

        // Conversations allows Jet FileSent but failed with session-terminate and reason = connectivity-error
        // So retry with HttpFileUpload if previously hasSecurityError
        if ((recipient != null)
                && (!OutgoingFileOfferJingleImpl.hasSecurityError() || (ChatFragment.MSGTYPE_OMEMO != chatType))) {
            OutgoingFileOfferController ofoController;
            int encType = IMessage.ENCRYPTION_NONE;
            String msgUuid = xferCon.getMessageUuid();
            Context ctx = aTalkApp.getGlobalContext();
            JingleFile jingleFile = createJingleFile(ctx, file);
            OmemoManager omemoManager = OmemoManager.getInstanceFor(mPPS.getConnection());

            try {
                if (ChatFragment.MSGTYPE_OMEMO == chatType) {
                    encType = IMessage.ENCRYPTION_OMEMO;
                    ofoController = jetManager.sendEncryptedFile(file, jingleFile, recipient, omemoManager);
                }
                else {
                    // For testing only: forced to use legacy file transfer for unencrypted content
                    // throw new OperationNotSupportedException("Use legacy File Transfer"));
                    ofoController = jingleFTManager.sendFile(file, jingleFile, recipient);
                }
                OutgoingFileOfferJingleImpl outgoingTransfer
                        = new OutgoingFileOfferJingleImpl(contact, file, msgUuid, ofoController, mPPS.getConnection());

                // Notify all interested listeners that a file transfer has been created.
                xferCon.setStatus(FileTransferStatusChangeEvent.IN_PROGRESS, contact, encType, "Jingle File Transfer");
                return outgoingTransfer;
            } catch (SSLHandshakeException ex) {
                throw new OperationNotSupportedException(ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
            } catch (UndecidedOmemoIdentityException e) {
                // Display dialog for use to verify omemoDevice; throw OperationNotSupportedException to use other methods for this file transfer.
                OmemoAuthenticateListener omemoAuthListener = new OmemoAuthenticateListener(recipient, omemoManager);
                ctx.startActivity(OmemoAuthenticateDialog.createIntent(ctx, omemoManager, e.getUndecidedDevices(), omemoAuthListener));
                throw new OperationNotSupportedException(e.getMessage());
            } catch (InterruptedException | XMPPException.XMPPErrorException | SmackException | IOException e) {
                throw new OperationNotSupportedException(e.getMessage());
            }
        }
        else {
            throw new OperationNotSupportedException(aTalkApp.getResString(R.string.service_gui_FILE_TRANSFER_NOT_SUPPORTED));
        }
    }

    /**
     * Create JingleFile from the given file
     *
     * @param file sending file
     * @return JingleFile metaData
     */
    private JingleFile createJingleFile(Context ctx, File file)
    {
        JingleFile jingleFile = null;
        String mimeType = FileBackend.getMimeType(ctx, Uri.fromFile(file));
        try {
            jingleFile = JingleFile.fromFile(file, null, mimeType, HashManager.ALGORITHM.SHA3_256);
        } catch (NoSuchAlgorithmException | IOException e) {
            Timber.e("JingleFile creation error: %s", e.getMessage());
        }
        return jingleFile;
    }

    /**
     * Omemo listener callback on user authentication for undecided omemoDevices
     */
    private static class OmemoAuthenticateListener implements OmemoAuthenticateDialog.AuthenticateListener
    {
        FullJid recipient;
        OmemoManager omemoManager;

        OmemoAuthenticateListener(FullJid recipient, OmemoManager omemoManager)
        {
            this.recipient = recipient;
            this.omemoManager = omemoManager;
        }

        @Override
        public void onAuthenticate(boolean allTrusted, Set<OmemoDevice> omemoDevices)
        {
            if (!allTrusted) {
                aTalkApp.showToastMessage(R.string.omemo_send_error,
                        "Undecided Omemo Identity: " + omemoDevices.toString());
            }
        }
    }

    /**
     * Http file upload if supported by the server
     *
     * @param file the file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param xferCon an instance of FileSendConversation
     * @return the <code>FileTransfer</code> or HTTPFileUpload object charged to transfer the given <code>file</code>.
     * @throws Exception if anything goes wrong
     */
    private Object httpFileUpload(File file, int chatType, FileSendConversation xferCon)
            throws Exception
    {
        // check to see if server supports httpFileUpload service if contact is off line or legacy file transfer failed
        if (httpFileUploadManager.isUploadServiceDiscovered()) {
            int encType = IMessage.ENCRYPTION_NONE;
            Object url;
            try {
                if (ChatFragment.MSGTYPE_OMEMO == chatType) {
                    encType = IMessage.ENCRYPTION_OMEMO;
                    url = httpFileUploadManager.uploadFileEncrypted(file, xferCon);
                }
                else {
                    url = httpFileUploadManager.uploadFile(file, xferCon);
                }
                xferCon.setStatus(FileTransferStatusChangeEvent.IN_PROGRESS, contact, encType, "HTTP File Upload");
                return url;
            } catch (SSLHandshakeException ex) {
                throw new OperationNotSupportedException(ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
            } catch (InterruptedException | XMPPException.XMPPErrorException | SmackException | IOException e) {
                throw new OperationNotSupportedException(e.getMessage());
            }
        }
        else
            throw new OperationNotSupportedException(aTalkApp.getResString(R.string.service_gui_FILE_TRANSFER_NOT_SUPPORTED));
    }

    /**
     * Returns the maximum file length supported by the protocol in bytes.
     *
     * @return the file length that is supported.
     */
    public long getMaximumFileLength()
    {
        return ftOpSet.getMaximumFileLength();
    }

    public void inviteChatContact(EntityBareJid contactAddress, String reason)
    {
    }

    /**
     * Returns the parent session of this chat transport. A <code>ChatSession</code> could contain
     * more than one transports.
     *
     * @return the parent session of this chat transport
     */
    public ChatSession getParentChatSession()
    {
        return parentChatSession;
    }

    /**
     * Adds an SMS message listener to this chat transport.
     *
     * @param l The message listener to add.
     */
    public void addSmsMessageListener(MessageListener l)
    {
        // If this chat transport does not support sms messaging we do nothing here.
        if (!allowsSmsMessage())
            return;

        OperationSetSmsMessaging smsOpSet = mPPS.getOperationSet(OperationSetSmsMessaging.class);
        smsOpSet.addMessageListener(l);
    }

    /**
     * Adds an instant message listener to this chat transport.
     * Special case for DomainJid to display received messages from server
     *
     * @param l The message listener to add.
     */
    public void addInstantMessageListener(MessageListener l)
    {
        // Skip if this chat transport does not support instant messaging; except if it is a DomainJid
        if (!allowsInstantMessage() && !(contact.getJid() instanceof DomainBareJid))
            return;

        OperationSetBasicInstantMessaging imOpSet = mPPS.getOperationSet(OperationSetBasicInstantMessaging.class);
        imOpSet.addMessageListener(l);
    }

    /**
     * Removes the given sms message listener from this chat transport.
     *
     * @param l The message listener to remove.
     */
    public void removeSmsMessageListener(MessageListener l)
    {
        // If this chat transport does not support sms messaging we do nothing here.
        if (!allowsSmsMessage())
            return;

        OperationSetSmsMessaging smsOpSet = mPPS.getOperationSet(OperationSetSmsMessaging.class);
        smsOpSet.removeMessageListener(l);
    }

    /**
     * Removes the instant message listener from this chat transport.
     *
     * @param l The message listener to remove.
     */
    public void removeInstantMessageListener(MessageListener l)
    {
        // Skip if this chat transport does not support instant messaging; except if it is a DomainJid
        if (!allowsInstantMessage() && !(contact.getJid() instanceof DomainBareJid))
            return;

        OperationSetBasicInstantMessaging imOpSet = mPPS.getOperationSet(OperationSetBasicInstantMessaging.class);
        imOpSet.removeMessageListener(l);
    }

    /**
     * Indicates that a contact has changed its status.
     *
     * @param evt The presence event containing information about the contact status change.
     */
    public void contactPresenceStatusChanged(ContactPresenceStatusChangeEvent evt)
    {
        // If the contactResource is set then the status will be updated from the MetaContactChatSession.
        // cmeng: contactResource condition removed to fix contact goes offline<->online // && (contactResource == null)
        if (evt.getSourceContact().equals(contact)
                && !evt.getOldStatus().equals(evt.getNewStatus())) {
            this.updateContactStatus();
        }
    }

    /**
     * Updates the status of this contact with the new given status.
     */
    private void updateContactStatus()
    {
        // Update the status of the given contact in the "send via" selector box.
        parentChatSession.getChatSessionRenderer().updateChatTransportStatus(this);
    }

    /**
     * Removes all previously added listeners.
     */
    public void dispose()
    {
        if (presenceOpSet != null)
            presenceOpSet.removeContactPresenceStatusListener(this);
    }

    /**
     * Returns the descriptor of this chat transport.
     *
     * @return the descriptor of this chat transport
     */
    public Object getDescriptor()
    {
        return contact;
    }

    /**
     * Sets the icon for the given file.
     *
     * @param file the file to set an icon for
     * @return the byte array containing the thumbnail
     */
    public static byte[] getFileThumbnail(File file)
    {
        byte[] imageData = null;

        if (FileBackend.isMediaFile(file)) {
            String imagePath = file.toString();
            try {
                FileInputStream fis = new FileInputStream(imagePath);
                Bitmap imageBitmap = BitmapFactory.decodeStream(fis);

                // check to ensure BitmapFactory can handle the MIME type
                if (imageBitmap != null) {
                    int width = imageBitmap.getWidth();
                    int height = imageBitmap.getHeight();

                    if (width > THUMBNAIL_WIDTH)
                        width = THUMBNAIL_WIDTH;
                    if (height > THUMBNAIL_HEIGHT)
                        height = THUMBNAIL_HEIGHT;

                    imageBitmap = ThumbnailUtils.extractThumbnail(imageBitmap, width, height);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                    imageData = baos.toByteArray();
                }
            } catch (FileNotFoundException e) {
                Timber.d("Could not locate image file. %s", e.getMessage());
            }
        }
        return imageData;
    }
}
