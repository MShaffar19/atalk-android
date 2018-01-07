/*
 * otr4j, the open source java otr library.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package net.java.otr4j.session;

import net.java.otr4j.*;
import net.java.otr4j.crypto.*;
import net.java.otr4j.io.*;
import net.java.otr4j.io.messages.*;
import net.java.otr4j.util.SelectableMap;
import net.java.sip.communicator.plugin.otr.*;
import net.java.sip.communicator.service.gui.Chat;

import org.atalk.android.*;
import org.atalk.android.gui.chat.ChatPanel;

import java.io.*;
import java.math.BigInteger;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.*;
import java.util.logging.*;

import javax.crypto.interfaces.DHPublicKey;

/**
 * @author George Politis
 * @author Danny van Heumen
 * @author Eng Chong Meng
 */
public class SessionImpl implements Session
{
	private static Logger logger = Logger.getLogger(SessionImpl.class.getName());

	private final SelectableMap<InstanceTag, SessionImpl> slaveSessions;
	private final boolean isMasterSession;
	private SessionID sessionID;
	private OtrEngineHost host;
	private SessionStatus sessionStatus;
	private AuthContext authContext;
	private SessionKeys[][] sessionKeys;
	private Vector<byte[]> oldMacKeys;
	private final OtrSm otrSm;
	private BigInteger ess;
	private OfferStatus offerStatus;
	private final InstanceTag senderTag;
	private InstanceTag receiverInstanceTag;
	private int protocolVersion;
	private final OtrAssembler assembler;
	private final OtrFragmenter fragmenter;
	private final List<OtrEngineListener> listeners = new Vector<>();
	private PublicKey remotePublicKey;

	public SessionImpl(SessionID sessionID, OtrEngineHost listener)
	{
		this.setSessionID(sessionID);
		this.setHost(listener);

		// client application calls OtrSessionManager.getSessionStatus()
		// -> create new session if it does not exist, end up here
		// -> setSessionStatus() fires statusChangedEvent
		// -> client application calls OtrSessionManager.getSessionStatus()
		this.sessionStatus = SessionStatus.PLAINTEXT;
		this.offerStatus = OfferStatus.idle;

		otrSm = new OtrSm(this, listener);
		this.senderTag = new InstanceTag();
		this.receiverInstanceTag = InstanceTag.ZERO_TAG;

		this.slaveSessions = new SelectableMap<>(new HashMap<InstanceTag, SessionImpl>());
		isMasterSession = true;

		assembler = new OtrAssembler(getSenderInstanceTag());
		fragmenter = new OtrFragmenter(this, listener);
	}

	// A private constructor for instantiating 'slave' sessions.
	private SessionImpl(SessionID sessionID, OtrEngineHost listener, InstanceTag senderTag,
			InstanceTag receiverInstanceTag)
	{
		this.setSessionID(sessionID);
		this.setHost(listener);

		this.sessionStatus = SessionStatus.PLAINTEXT;
		this.offerStatus = OfferStatus.idle;

		otrSm = new OtrSm(this, listener);
		this.senderTag = senderTag;
		this.receiverInstanceTag = receiverInstanceTag;

		this.slaveSessions = new SelectableMap<>(Collections.<InstanceTag, SessionImpl>emptyMap());
		isMasterSession = false;

		protocolVersion = OTRv.THREE;
		assembler = new OtrAssembler(getSenderInstanceTag());
		fragmenter = new OtrFragmenter(this, listener);
	}

	@Override
	public BigInteger getS()
	{
		return ess;
	}

	private SessionKeys getEncryptionSessionKeys()
	{
		logger.finest("Getting encryption keys");
		return getSessionKeysByIndex(SessionKeys.Previous, SessionKeys.Current);
	}

	private SessionKeys getMostRecentSessionKeys()
	{
		logger.finest("Getting most recent keys.");
		return getSessionKeysByIndex(SessionKeys.Current, SessionKeys.Current);
	}

	private SessionKeys getSessionKeysByID(int localKeyID, int remoteKeyID)
	{
		logger.log(Level.FINEST, "Searching for session keys with (localKeyID, remoteKeyID)" +
				" = ({0},{1})", new Object[]{localKeyID, remoteKeyID});

		for (int i = 0; i < getSessionKeys().length; i++) {
			for (int j = 0; j < getSessionKeys()[i].length; j++) {
				SessionKeys current = getSessionKeysByIndex(i, j);
				if (current.getLocalKeyID() == localKeyID
						&& current.getRemoteKeyID() == remoteKeyID) {
					logger.finest("Matching keys found.");
					return current;
				}
			}
		}
		return null;
	}

	private SessionKeys getSessionKeysByIndex(int localKeyIndex, int remoteKeyIndex)
	{
		if (getSessionKeys()[localKeyIndex][remoteKeyIndex] == null)
			getSessionKeys()[localKeyIndex][remoteKeyIndex]
				= new SessionKeysImpl(localKeyIndex, remoteKeyIndex);

		return getSessionKeys()[localKeyIndex][remoteKeyIndex];
	}

	private void rotateRemoteSessionKeys(DHPublicKey pubKey)
			throws OtrException
	{

		logger.finest("Rotating remote keys.");
		SessionKeys sess1 = getSessionKeysByIndex(SessionKeys.Current, SessionKeys.Previous);
		if (sess1.getIsUsedReceivingMACKey()) {
			logger.finest("Detected used Receiving MAC key. Adding to old MAC keys to reveal it.");
			getOldMacKeys().add(sess1.getReceivingMACKey());
		}

		SessionKeys sess2 = getSessionKeysByIndex(SessionKeys.Previous, SessionKeys.Previous);
		if (sess2.getIsUsedReceivingMACKey()) {
			logger.finest("Detected used Receiving MAC key. Adding to old MAC keys to reveal it.");
			getOldMacKeys().add(sess2.getReceivingMACKey());
		}

		SessionKeys sess3 = getSessionKeysByIndex(SessionKeys.Current, SessionKeys.Current);
		sess1.setRemoteDHPublicKey(sess3.getRemoteKey(), sess3.getRemoteKeyID());

		SessionKeys sess4 = getSessionKeysByIndex(SessionKeys.Previous, SessionKeys.Current);
		sess2.setRemoteDHPublicKey(sess4.getRemoteKey(), sess4.getRemoteKeyID());
		sess3.setRemoteDHPublicKey(pubKey, sess3.getRemoteKeyID() + 1);
		sess4.setRemoteDHPublicKey(pubKey, sess4.getRemoteKeyID() + 1);
	}

	private void rotateLocalSessionKeys()
			throws OtrException
	{
		logger.finest("Rotating local keys.");
		SessionKeys sess1 = getSessionKeysByIndex(SessionKeys.Previous, SessionKeys.Current);
		if (sess1.getIsUsedReceivingMACKey()) {
			logger.finest("Detected used Receiving MAC key. Adding to old MAC keys to reveal it.");
			getOldMacKeys().add(sess1.getReceivingMACKey());
		}

		SessionKeys sess2 = getSessionKeysByIndex(SessionKeys.Previous, SessionKeys.Previous);
		if (sess2.getIsUsedReceivingMACKey()) {
			logger.finest("Detected used Receiving MAC key. Adding to old MAC keys to reveal it.");
			getOldMacKeys().add(sess2.getReceivingMACKey());
		}

		SessionKeys sess3 = getSessionKeysByIndex(SessionKeys.Current, SessionKeys.Current);
		sess1.setLocalPair(sess3.getLocalPair(), sess3.getLocalKeyID());
		SessionKeys sess4 = getSessionKeysByIndex(SessionKeys.Current, SessionKeys.Previous);
		sess2.setLocalPair(sess4.getLocalPair(), sess4.getLocalKeyID());

		KeyPair newPair = new OtrCryptoEngineImpl().generateDHKeyPair();
		sess3.setLocalPair(newPair, sess3.getLocalKeyID() + 1);
		sess4.setLocalPair(newPair, sess4.getLocalKeyID() + 1);
	}

	private byte[] collectOldMacKeys()
	{
		logger.finest("Collecting old MAC keys to be revealed.");
		int len = 0;
		for (int i = 0; i < getOldMacKeys().size(); i++)
			len += getOldMacKeys().get(i).length;

		ByteBuffer buff = ByteBuffer.allocate(len);
		for (int i = 0; i < getOldMacKeys().size(); i++)
			buff.put(getOldMacKeys().get(i));

		getOldMacKeys().clear();
		return buff.array();
	}

	private void setSessionStatus(SessionStatus sessionStatus)
			throws OtrException
	{
		switch (sessionStatus) {
			case ENCRYPTED:
				AuthContext auth = this.getAuthContext();
				ess = auth.getS();
				logger.finest("Setting most recent session keys from auth.");
				for (int i = 0; i < this.getSessionKeys()[0].length; i++) {
					SessionKeys current = getSessionKeysByIndex(0, i);
					current.setLocalPair(auth.getLocalDHKeyPair(), 1);
					current.setRemoteDHPublicKey(auth.getRemoteDHPublicKey(), 1);
					current.setS(auth.getS());
				}

				KeyPair nextDH = new OtrCryptoEngineImpl().generateDHKeyPair();
				for (int i = 0; i < this.getSessionKeys()[1].length; i++) {
					SessionKeys current = getSessionKeysByIndex(1, i);
					current.setRemoteDHPublicKey(auth.getRemoteDHPublicKey(), 1);
					current.setLocalPair(nextDH, 2);
				}
				this.setRemotePublicKey(auth.getRemoteLongTermPublicKey());
				auth.reset();
				otrSm.reset();
				break;
			case FINISHED:
			case PLAINTEXT:
				break;
			default:
				throw new UnsupportedOperationException("What to do for this state?");
		}

		if (sessionStatus == this.sessionStatus)
			return;

		this.sessionStatus = sessionStatus;
		for (OtrEngineListener l : this.listeners)
			l.sessionStatusChanged(getSessionID());
	}

	@Override
	public SessionStatus getSessionStatus()
	{
		if (this.slaveSessions.isSelected() && getProtocolVersion() == OTRv.THREE) {
			return this.slaveSessions.getSelected().getSessionStatus();
		}
		return sessionStatus;
	}

	private void setSessionID(SessionID sessionID)
	{
		this.sessionID = sessionID;
	}

	@Override
	public SessionID getSessionID()
	{
		return sessionID;
	}

	private void setHost(OtrEngineHost host)
	{
		this.host = host;
	}

	private OtrEngineHost getHost()
	{
		return host;
	}

	private SessionKeys[][] getSessionKeys()
	{
		if (sessionKeys == null)
			sessionKeys = new SessionKeys[2][2];
		return sessionKeys;
	}

	private AuthContext getAuthContext()
	{
		if (authContext == null)
			authContext = new AuthContextImpl(this);
		return authContext;
	}

	private Vector<byte[]> getOldMacKeys()
	{
		if (oldMacKeys == null)
			oldMacKeys = new Vector<>();
		return oldMacKeys;
	}

	@Override
	public String transformReceiving(String msgText)
			throws OtrException
	{
		OtrPolicy policy = getSessionPolicy();
		if (!policy.getAllowV1() && !policy.getAllowV2() && !policy.getAllowV3()) {
			logger.finest("Policy does not allow neither V1 nor V2 & V3, ignoring message.");
			return msgText;
		}

		try {
			msgText = assembler.accumulate(msgText);
		}
		catch (UnknownInstanceException e) {
			// The fragment is not intended for us
			logger.finest(e.getMessage());
			getHost().messageFromAnotherInstanceReceived(getSessionID());
			return null;
		}
		catch (ProtocolException e) {
			logger.warning("An invalid message fragment was discarded.");
			return null;
		}

		if (msgText == null)
			return null; // Not a complete message (yet).

		AbstractMessage m;
		try {
			m = SerializationUtils.toMessage(msgText);
		}
		catch (IOException e) {
			throw new OtrException(e);
		}
		if (m == null)
			return msgText; // Probably null or empty.

		if (m.messageType != AbstractMessage.MESSAGE_PLAINTEXT)
			offerStatus = OfferStatus.accepted;
		else if (offerStatus == OfferStatus.sent)
			offerStatus = OfferStatus.rejected;

		if (m instanceof AbstractEncodedMessage && isMasterSession) {
			AbstractEncodedMessage encodedM = (AbstractEncodedMessage) m;
			if (encodedM.protocolVersion == OTRv.THREE) {
				if (encodedM.receiverInstanceTag != this.getSenderInstanceTag().getValue()
						&& !(encodedM.messageType == AbstractEncodedMessage.MESSAGE_DH_COMMIT
						&& encodedM.receiverInstanceTag == 0)) {
					// The message is not intended for us. Discarding...
					logger.finest("Received an encoded message with receiver instance tag"
							+ " that is different from ours, ignore this message");
					getHost().messageFromAnotherInstanceReceived(getSessionID());
					return null;
				}
			}

			if (encodedM.senderInstanceTag != this.getReceiverInstanceTag().getValue()
					&& this.getReceiverInstanceTag().getValue() != 0) {
				// Message is intended for us but is coming from a different instance.
				// We relay this message to the appropriate session for transforming.
				logger.finest("Received an encoded message from a different instance. "
						+ "Our buddy may  be logged from multiple locations.");

				InstanceTag newReceiverTag = new InstanceTag(encodedM.senderInstanceTag);
				synchronized (slaveSessions) {

					if (!slaveSessions.containsKey(newReceiverTag)) {
						final SessionImpl session = new SessionImpl(sessionID, getHost(),
								getSenderInstanceTag(), newReceiverTag);

						if (encodedM.messageType == AbstractEncodedMessage.MESSAGE_DHKEY) {
							session.getAuthContext().set(this.getAuthContext());
						}

						session.addOtrEngineListener(new OtrEngineListener()
						{
							@Override
							public void sessionStatusChanged(SessionID sessionID)
							{
								for (OtrEngineListener l : listeners)
									l.sessionStatusChanged(sessionID);
							}

							@Override
							public void multipleInstancesDetected(SessionID sessionID)
							{
							}

							@Override
							public void outgoingSessionChanged(SessionID sessionID)
							{
							}
						});

						slaveSessions.put(newReceiverTag, session);
						getHost().multipleInstancesDetected(sessionID);
						for (OtrEngineListener l : listeners)
							l.multipleInstancesDetected(sessionID);
					}
				}
				return slaveSessions.get(newReceiverTag).transformReceiving(msgText);
			}
		}

		switch (m.messageType) {
			case AbstractEncodedMessage.MESSAGE_DATA:
				return handleDataMessage((DataMessage) m);
			case AbstractMessage.MESSAGE_ERROR:
				handleErrorMessage((ErrorMessage) m);
				return null;
			case AbstractMessage.MESSAGE_PLAINTEXT:
				return handlePlainTextMessage((PlainTextMessage) m);
			case AbstractMessage.MESSAGE_QUERY:
				handleQueryMessage((QueryMessage) m);
				return null;
			case AbstractEncodedMessage.MESSAGE_DH_COMMIT:
			case AbstractEncodedMessage.MESSAGE_DHKEY:
			case AbstractEncodedMessage.MESSAGE_REVEALSIG:
			case AbstractEncodedMessage.MESSAGE_SIGNATURE:
				AuthContext auth = this.getAuthContext();
				auth.handleReceivingMessage(m);
				if (auth.getIsSecure()) {
					this.setSessionStatus(SessionStatus.ENCRYPTED);
					logger.finest("Gone Secure.");
				}
				return null;
			default:
				throw new UnsupportedOperationException("Received an unknown message type.");
		}
	}

	private void sendingDHCommitMessage(final QueryMessage queryMessage, final boolean supportV1)
			throws OtrException
	{
		// OTR setup request from buddy is disabled when omemo is ongoing
		OtrContactManager.OtrContact otrContact = ScOtrEngineImpl.getOtrContact(sessionID);
		Chat chat = OtrActivator.uiService.getChat(otrContact.contact);
		if (((ChatPanel) chat).isOmemoChat()) {
			String msg_local = aTalkApp.getGlobalContext()
					.getString(R.string.crypto_msg_OMEMO_SESSION_OTR_NOT_ALLOW);
			getHost().showAlert(getSessionID(), msg_local);
			String msg_remote = aTalkApp.getGlobalContext()
					.getString(R.string.crypto_msg_OMEMO_SESSION_OTR_NOT_ALLOW_SENDER);
			getHost().injectMessage(getSessionID(), msg_remote);
			return;
		}

		OtrPolicy policy = getSessionPolicy();
		if (queryMessage.versions.contains(OTRv.THREE) && policy.getAllowV3()) {
			logger.finest("V3 message tag found and supported.");
			DHCommitMessage dhCommit = getAuthContext().respondAuth(OTRv.THREE);
			if (isMasterSession) {
				for (SessionImpl session : slaveSessions.values()) {
					session.getAuthContext().reset();
					session.getAuthContext().set(this.getAuthContext());
				}
			}
			logger.finest("Sending D-H Commit Message");
			injectMessage(dhCommit);
		}
		else if (queryMessage.versions.contains(OTRv.TWO) && policy.getAllowV2()) {
			logger.finest("V2 message tag found and supported.");
			DHCommitMessage dhCommit = getAuthContext().respondAuth(OTRv.TWO);
			logger.finest("Sending D-H Commit Message");
			injectMessage(dhCommit);
		}
		else if (queryMessage.versions.contains(OTRv.ONE) && policy.getAllowV1()) {
			if (supportV1) {
				logger.finest("V1 message tag found and supported. - ignoring.");
			}
			else {
				logger.finest("V1 message tag found but not supported.");
				throw new UnsupportedOperationException();
			}
		}
	}

	private void handleQueryMessage(QueryMessage queryMessage)
			throws OtrException
	{
		logger.log(Level.FINEST, "{0} received a query message from {1} through {2}.",
				new Object[]{getSessionID().getAccountID(), getSessionID().getUserID(),
						getSessionID().getProtocolName()});
		sendingDHCommitMessage(queryMessage, true);
	}

	private void handleErrorMessage(ErrorMessage errorMessage)
			throws OtrException
	{
		logger.log(Level.FINEST, "{0} received an error message from {1} through {2}.",
				new Object[]{getSessionID().getAccountID(), getSessionID().getUserID(),
						getSessionID().getProtocolName()});

		getHost().showError(this.getSessionID(), errorMessage.error);

		OtrPolicy policy = getSessionPolicy();
		if (policy.getErrorStartAKE()) {
			logger.finest("Error message starts AKE.");
			List<Integer> versions = new ArrayList<>();
			if (policy.getAllowV1())
				versions.add(OTRv.ONE);

			if (policy.getAllowV2())
				versions.add(OTRv.TWO);

			if (policy.getAllowV3())
				versions.add(OTRv.THREE);

			logger.finest("Sending Query");
			injectMessage(new QueryMessage(versions));
		}
	}

	private String handleDataMessage(DataMessage data)
			throws OtrException
	{
		logger.log(Level.FINEST, "{0} received a data message from {1}.",
				new Object[] {getSessionID().getAccountID(), getSessionID().getUserID()});

		switch (this.getSessionStatus()) {
			case ENCRYPTED:
				logger.finest("Message state is ENCRYPTED. Trying to decrypt message.");
				// Find matching session keys.
				int senderKeyID = data.senderKeyID;
				int recipientKeyID = data.recipientKeyID;
				SessionKeys matchingKeys = this.getSessionKeysByID(recipientKeyID, senderKeyID);

				if (matchingKeys == null) {
					logger.finest("No matching keys found.");
					getHost().unreadableMessageReceived(this.getSessionID());
					injectMessage(new ErrorMessage(AbstractMessage.MESSAGE_ERROR,
							getHost().getReplyForUnreadableMessage(getSessionID())));
					return null;
				}

				// Verify received MAC with a locally calculated MAC.
				logger.finest("Transforming T to byte[] to calculate it's HmacSHA1.");
				byte[] serializedT;
				try {
					serializedT = SerializationUtils.toByteArray(data.getT());
				}
				catch (IOException e) {
					throw new OtrException(e);
				}
				OtrCryptoEngine otrCryptoEngine = new OtrCryptoEngineImpl();

				byte[] computedMAC = otrCryptoEngine.sha1Hmac(serializedT,
						matchingKeys.getReceivingMACKey(), SerializationConstants.TYPE_LEN_MAC);
				if (!Arrays.equals(computedMAC, data.mac)) {
					logger.finest("MAC verification failed, ignoring message");
					getHost().unreadableMessageReceived(this.getSessionID());
					injectMessage(new ErrorMessage(AbstractMessage.MESSAGE_ERROR,
							getHost().getReplyForUnreadableMessage(getSessionID())));
					return null;
				}
				logger.finest("Computed HmacSHA1 value matches sent one.");

				// Mark this MAC key as old to be revealed.
				matchingKeys.setIsUsedReceivingMACKey(true);
				matchingKeys.setReceivingCtr(data.ctr);
				byte[] dmc = otrCryptoEngine.aesDecrypt(matchingKeys.getReceivingAESKey(),
						matchingKeys.getReceivingCtr(), data.encryptedMessage);

				String decryptedMsgContent;
				try {
					// Expect bytes to be text encoded in UTF-8.
					decryptedMsgContent = new String(dmc, "UTF-8");
				}
				catch (UnsupportedEncodingException e) {
					throw new OtrException(e);
				}
				logger.finest("Decrypted message: \"" + decryptedMsgContent + "\"");

				// Rotate keys if necessary.
				SessionKeys mostRecent = this.getMostRecentSessionKeys();
				if (mostRecent.getLocalKeyID() == recipientKeyID)
					this.rotateLocalSessionKeys();

				if (mostRecent.getRemoteKeyID() == senderKeyID)
					this.rotateRemoteSessionKeys(data.nextDH);

				// Handle TLVs
				List<TLV> tlvs = null;
				int tlvIndex = decryptedMsgContent.indexOf((char) 0x0);
				if (tlvIndex > -1) {
					decryptedMsgContent = decryptedMsgContent.substring(0, tlvIndex);
					tlvIndex++;
					byte[] tlvsb = new byte[dmc.length - tlvIndex];
					System.arraycopy(dmc, tlvIndex, tlvsb, 0, tlvsb.length);

					tlvs = new LinkedList<>();
					ByteArrayInputStream tin = new ByteArrayInputStream(tlvsb);
					while (tin.available() > 0) {
						int type;
						byte[] tdata;
						OtrInputStream eois = new OtrInputStream(tin);
						try {
							type = eois.readShort();
							tdata = eois.readTlvData();
							eois.close();
						}
						catch (IOException e) {
							throw new OtrException(e);
						}
						tlvs.add(new TLV(type, tdata));
					}
				}
				if (tlvs != null && tlvs.size() > 0) {
					for (TLV tlv : tlvs) {
						switch (tlv.getType()) {
							case TLV.DISCONNECTED:
								this.setSessionStatus(SessionStatus.FINISHED);
								return null;
							default:
								if (otrSm.doProcessTlv(tlv))
									return null;
						}
					}
				}
				return decryptedMsgContent;
			case FINISHED:
			case PLAINTEXT:
				getHost().unreadableMessageReceived(this.getSessionID());
				injectMessage(new ErrorMessage(AbstractMessage.MESSAGE_ERROR,
						getHost().getReplyForUnreadableMessage(getSessionID())));
				break;
			default:
				throw new UnsupportedOperationException("What to do for this state?");
		}
		return null;
	}

	@Override
	public void injectMessage(AbstractMessage m)
			throws OtrException
	{
		String msg;
		try {
			msg = SerializationUtils.toString(m);
		}
		catch (IOException e) {
			throw new OtrException(e);
		}
		if (m instanceof QueryMessage)
			msg += getHost().getFallbackMessage(getSessionID());

		if (SerializationUtils.otrEncoded(msg)) {
			// Content is OTR encoded, so we are allowed to partition.
			String[] fragments;
			try {
				fragments = this.fragmenter.fragment(msg);
				for (String fragment : fragments) {
					getHost().injectMessage(getSessionID(), fragment);
				}
			}
			catch (IOException e) {
				logger.warning("Failed to fragment message according to provided instructions.");
				throw new OtrException(e);
			}
		}
		else {
			getHost().injectMessage(getSessionID(), msg);
		}
	}

	private String handlePlainTextMessage(PlainTextMessage plainTextMessage)
			throws OtrException
	{
		logger.log(Level.FINEST, "{0} received a plaintext message from {1} through {2}.",
				new Object[] {getSessionID().getAccountID(), getSessionID().getUserID(),
					getSessionID().getProtocolName()});

		OtrPolicy policy = getSessionPolicy();
		List<Integer> versions = plainTextMessage.versions;
		if (versions == null || versions.size() < 1) {
			logger.finest("Received plaintext message without the whitespace tag.");

			switch (this.getSessionStatus()) {
				case ENCRYPTED:
				case FINISHED:
					// Display the message to the user, but warn him that the message was received
					// non-encrypted.
					getHost().unencryptedMessageReceived(sessionID, plainTextMessage.cleanText);
					return plainTextMessage.cleanText;
				case PLAINTEXT:
					// Simply display the message to the user. If REQUIRE_ENCRYPTION is set, warn
					// him that the message was received non-encrypted.
					if (policy.getRequireEncryption()) {
						getHost().unencryptedMessageReceived(sessionID,
								plainTextMessage.cleanText);
					}
					return plainTextMessage.cleanText;
				default:
					throw new UnsupportedOperationException("What to do for this state?");
			}
		}
		else {
			logger.finest("Received plaintext message with the whitespace tag.");
			switch (this.getSessionStatus()) {
				case ENCRYPTED:
				case FINISHED:
					// Remove the whitespace tag and display the message to the user, but warn
					// him that the message was received non-encrypted.
					getHost().unencryptedMessageReceived(sessionID, plainTextMessage
							.cleanText);
					break;
				case PLAINTEXT:
					// Remove the whitespace tag and display the message to the user. If
					// REQUIRE_ENCRYPTION is set, warn him that the message was received
					// non-encrypted.
					if (policy.getRequireEncryption())
						getHost().unencryptedMessageReceived(sessionID,
								plainTextMessage.cleanText);
					break;
				default:
					throw new UnsupportedOperationException("What to do for this state?");
			}
			if (policy.getWhitespaceStartAKE()) {
				logger.finest("WHITESPACE_START_AKE is set");
				try {
					sendingDHCommitMessage(plainTextMessage, false);
				}
				catch (OtrException ex) {
					ex.printStackTrace();
				}
			}
		}
		return plainTextMessage.cleanText;
	}

	@Override
	public String[] transformSending(String msgText)
			throws OtrException
	{
		return this.transformSending(msgText, null);
	}

	@Override
	public String[] transformSending(String msgText, List<TLV> tlvs)
			throws OtrException
	{
		if (isMasterSession && this.slaveSessions.isSelected()
				&& getProtocolVersion() == OTRv.THREE) {
			return this.slaveSessions.getSelected().transformSending(msgText, tlvs);
		}

		switch (this.getSessionStatus()) {
			case PLAINTEXT:
				OtrPolicy otrPolicy = getSessionPolicy();
				if (otrPolicy.getRequireEncryption()) {
					this.startSession();
					getHost().requireEncryptedMessage(sessionID, msgText);
					return null;
				}
				else {
					if (otrPolicy.getSendWhitespaceTag() && offerStatus != OfferStatus.rejected) {
						offerStatus = OfferStatus.sent;
						List<Integer> versions = new ArrayList<>(3);
						if (otrPolicy.getAllowV1())
							versions.add(OTRv.ONE);
						if (otrPolicy.getAllowV2())
							versions.add(OTRv.TWO);
						if (otrPolicy.getAllowV3())
							versions.add(OTRv.THREE);
						if (versions.isEmpty())
							versions = null;
						AbstractMessage abstractMessage
							= new PlainTextMessage(versions, msgText);
						try {
							return new String[]{SerializationUtils.toString(abstractMessage)};
						}
						catch (IOException e) {
							throw new OtrException(e);
						}
					}
					else {
						return new String[]{msgText};
					}
				}
			case ENCRYPTED:
			logger.log(Level.FINEST, "{0} sends an encrypted message to {1} through {2}.",
					new Object[] {getSessionID().getAccountID(), getSessionID().getUserID(),
						getSessionID().getProtocolName()});

				// Get encryption keys.
				SessionKeys encryptionKeys = this.getEncryptionSessionKeys();
				int senderKeyID = encryptionKeys.getLocalKeyID();
				int recipientKeyID = encryptionKeys.getRemoteKeyID();

				// Increment CTR.
				encryptionKeys.incrementSendingCtr();
				byte[] ctr = encryptionKeys.getSendingCtr();

				ByteArrayOutputStream out = new ByteArrayOutputStream();
				if (msgText != null && msgText.length() > 0) {
					try {
						out.write(msgText.getBytes("UTF8"));
					}
					catch (IOException e) {
						throw new OtrException(e);
					}
				}

				// Append tlvs
				if (tlvs != null && tlvs.size() > 0) {
					out.write((byte) 0x00);
					OtrOutputStream eoos = new OtrOutputStream(out);
					for (TLV tlv : tlvs) {
						try {
							eoos.writeShort(tlv.type);
							eoos.writeTlvData(tlv.value);
						}
						catch (IOException e) {
							throw new OtrException(e);
						}
					}
				}

				OtrCryptoEngine otrCryptoEngine = new OtrCryptoEngineImpl();
				byte[] data = out.toByteArray();
				// Encrypt message.
				logger.log(Level.FINEST,
					"Encrypting message with keyids (localKeyID, remoteKeyID) = ({0}, {1})",
					new Object[] {senderKeyID, recipientKeyID});
				byte[] encryptedMsg
						= otrCryptoEngine.aesEncrypt(encryptionKeys.getSendingAESKey(), ctr, data);

				// Get most recent keys to get the next D-H public key.
				SessionKeys mostRecentKeys = this.getMostRecentSessionKeys();
				DHPublicKey nextDH = (DHPublicKey) mostRecentKeys.getLocalPair().getPublic();

				// Calculate T.
				MysteriousT t = new MysteriousT(this.protocolVersion,
						getSenderInstanceTag().getValue(), getReceiverInstanceTag().getValue(), 0,
						senderKeyID, recipientKeyID, nextDH, ctr, encryptedMsg);

				// Calculate T hash.
				byte[] sendingMACKey = encryptionKeys.getSendingMACKey();

				logger.finest("Transforming T to byte[] to calculate it's HmacSHA1.");
				byte[] serializedT;
				try {
					serializedT = SerializationUtils.toByteArray(t);
				}
				catch (IOException e) {
					throw new OtrException(e);
				}

				byte[] mac = otrCryptoEngine.sha1Hmac(serializedT, sendingMACKey,
						SerializationConstants.TYPE_LEN_MAC);

				// Get old MAC keys to be revealed.
				byte[] oldKeys = this.collectOldMacKeys();
				DataMessage m = new DataMessage(t, mac, oldKeys);
				m.senderInstanceTag = getSenderInstanceTag().getValue();
				m.receiverInstanceTag = getReceiverInstanceTag().getValue();

				try {
					final String completeMessage = SerializationUtils.toString(m);
					return this.fragmenter.fragment(completeMessage);
				}
				catch (IOException e) {
					throw new OtrException(e);
				}
			case FINISHED:
				getHost().finishedSessionMessage(sessionID, msgText);
				return null;
			default:
				logger.finest("Unknown message state, not processing.");
				return new String[]{msgText};
		}
	}

	@Override
	public void startSession()
			throws OtrException
	{
		if (this.slaveSessions.isSelected() && getProtocolVersion() == OTRv.THREE) {
			this.slaveSessions.getSelected().startSession();
			return;
		}
		if (this.getSessionStatus() == SessionStatus.ENCRYPTED)
			return;

		if (!getSessionPolicy().getAllowV2() && !getSessionPolicy().getAllowV3())
			throw new UnsupportedOperationException();

		this.getAuthContext().startAuth();
	}

	@Override
	public void endSession()
			throws OtrException
	{
		if (this.slaveSessions.isSelected() && getProtocolVersion() == OTRv.THREE) {
			this.slaveSessions.getSelected().endSession();
			return;
		}
		SessionStatus status = this.getSessionStatus();
		switch (status) {
			case ENCRYPTED:
				List<TLV> tlvs = new ArrayList<>(1);
				tlvs.add(new TLV(TLV.DISCONNECTED, null));

				String[] msg = this.transformSending(null, tlvs);
				for (String part : msg) {
					getHost().injectMessage(getSessionID(), part);
				}
				this.setSessionStatus(SessionStatus.PLAINTEXT);
				break;
			case FINISHED:
				this.setSessionStatus(SessionStatus.PLAINTEXT);
				break;
			case PLAINTEXT:
				break;
			default:
				throw new UnsupportedOperationException("What to do for this state?");
		}
	}

	@Override
	public void refreshSession()
			throws OtrException
	{
		this.endSession();
		this.startSession();
	}

	private void setRemotePublicKey(PublicKey pubKey)
	{
		this.remotePublicKey = pubKey;
	}

	@Override
	public PublicKey getRemotePublicKey()
	{
		if (this.slaveSessions.isSelected() && getProtocolVersion() == OTRv.THREE)
			return this.slaveSessions.getSelected().getRemotePublicKey();
		return remotePublicKey;
	}

	@Override
	public void addOtrEngineListener(OtrEngineListener l)
	{
		synchronized (listeners) {
			if (!listeners.contains(l))
				listeners.add(l);
		}
	}

	@Override
	public void removeOtrEngineListener(OtrEngineListener l)
	{
		synchronized (listeners) {
			listeners.remove(l);
		}
	}

	@Override
	public OtrPolicy getSessionPolicy()
	{
		return getHost().getSessionPolicy(getSessionID());
	}

	@Override
	public KeyPair getLocalKeyPair()
			throws OtrException
	{
		return getHost().getLocalKeyPair(this.getSessionID());
	}

	@Override
	public void initSmp(String question, String secret)
			throws OtrException
	{
		if (this.slaveSessions.isSelected() && getProtocolVersion() == OTRv.THREE) {
			this.slaveSessions.getSelected().initSmp(question, secret);
			return;
		}
		if (this.getSessionStatus() != SessionStatus.ENCRYPTED)
			return;
		List<TLV> tlvs = otrSm.initRespondSmp(question, secret, true);
		String[] msg = transformSending("", tlvs);
		for (String part : msg) {
			getHost().injectMessage(getSessionID(), part);
		}
	}

	@Override
	public void respondSmp(String question, String secret)
			throws OtrException
	{
		if (this.slaveSessions.isSelected() && getProtocolVersion() == OTRv.THREE) {
			this.slaveSessions.getSelected().respondSmp(question, secret);
			return;
		}
		if (this.getSessionStatus() != SessionStatus.ENCRYPTED)
			return;
		List<TLV> tlvs = otrSm.initRespondSmp(question, secret, false);
		String[] msg = transformSending("", tlvs);
		for (String part : msg) {
			getHost().injectMessage(getSessionID(), part);
		}
	}

	@Override
	public void abortSmp()
			throws OtrException
	{
		if (this.slaveSessions.isSelected() && getProtocolVersion() == OTRv.THREE) {
			this.slaveSessions.getSelected().abortSmp();
			return;
		}
		if (this.getSessionStatus() != SessionStatus.ENCRYPTED)
			return;
		List<TLV> tlvs = otrSm.abortSmp();
		String[] msg = transformSending("", tlvs);
		for (String part : msg) {
			getHost().injectMessage(getSessionID(), part);
		}
	}

	@Override
	public boolean isSmpInProgress()
	{
		if (this.slaveSessions.isSelected() && getProtocolVersion() == OTRv.THREE)
			return this.slaveSessions.getSelected().isSmpInProgress();
		return otrSm.isSmpInProgress();
	}

	@Override
	public InstanceTag getSenderInstanceTag()
	{
		return senderTag;
	}

	@Override
	public InstanceTag getReceiverInstanceTag()
	{
		return receiverInstanceTag;
	}

	@Override
	public void setReceiverInstanceTag(InstanceTag receiverInstanceTag)
	{
		// ReceiverInstanceTag of a slave session is not supposed to change
		if (!isMasterSession)
			return;
		this.receiverInstanceTag = receiverInstanceTag;
	}

	@Override
	public void setProtocolVersion(int protocolVersion)
	{
		// Protocol version of a slave session is not supposed to change
		if (!isMasterSession)
			return;
		this.protocolVersion = protocolVersion;
	}

	@Override
	public int getProtocolVersion()
	{
		return isMasterSession ? this.protocolVersion : OTRv.THREE;
	}

	@Override
	public List<Session> getInstances()
	{
		List<Session> result = new ArrayList<>();
		result.add(this);
		result.addAll(slaveSessions.values());
		return result;
	}

	@Override
	public boolean setOutgoingInstance(InstanceTag tag)
	{
		// Only master session can set the outgoing session.
		if (!isMasterSession)
			return false;
		if (tag.equals(getReceiverInstanceTag())) {
			this.slaveSessions.deselect();
			for (OtrEngineListener l : listeners)
				l.outgoingSessionChanged(sessionID);
			return true;
		}

		if (slaveSessions.containsKey(tag)) {
			slaveSessions.select(tag);
			for (OtrEngineListener l : listeners) {
				l.outgoingSessionChanged(sessionID);
			}
			return true;
		}
		else {
			this.slaveSessions.deselect();
			return false;
		}
	}

	@Override
	public void respondSmp(InstanceTag receiverTag, String question, String secret)
			throws OtrException
	{
		if (receiverTag.equals(getReceiverInstanceTag())) {
			respondSmp(question, secret);
		}
		else {
			Session slave = slaveSessions.get(receiverTag);
			if (slave != null)
				slave.respondSmp(question, secret);
			else
				respondSmp(question, secret);
		}
	}

	@Override
	public SessionStatus getSessionStatus(InstanceTag tag)
	{
		if (tag.equals(getReceiverInstanceTag()))
			return sessionStatus;
		else {
			Session slave = slaveSessions.get(tag);
			return slave != null ? slave.getSessionStatus() : sessionStatus;
		}
	}

	@Override
	public PublicKey getRemotePublicKey(InstanceTag tag)
	{
		if (tag.equals(getReceiverInstanceTag()))
			return remotePublicKey;
		else {
			Session slave = slaveSessions.get(tag);
			return slave != null ? slave.getRemotePublicKey() : remotePublicKey;
		}
	}

	@Override
	public Session getOutgoingInstance()
	{
		if (this.slaveSessions.isSelected()) {
			return this.slaveSessions.getSelected();
		}
		else {
			return this;
		}
	}
}
