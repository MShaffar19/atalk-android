/*
 * Copyright 2017-2021 Florian Schmaus
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
package org.jivesoftware.smackx.jingle.provider;

import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.parsing.StandardExtensionElementProvider;
import org.jivesoftware.smack.provider.IqProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.*;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jivesoftware.smackx.DefaultExtensionElementProvider;
import org.jivesoftware.smackx.DefaultXmlElementProvider;
import org.jivesoftware.smackx.jingle.SdpSource;
import org.jivesoftware.smackx.colibri.WebSocketExtension;
import org.jivesoftware.smackx.confdesc.CallIdExtension;
import org.jivesoftware.smackx.confdesc.ConferenceDescriptionExtension;
import org.jivesoftware.smackx.jingle.*;
import org.jivesoftware.smackx.jingle.element.*;
import org.jivesoftware.smackx.jingle.element.JingleReason.Reason;
import org.jivesoftware.smackx.jitsimeet.BundleExtension;
import org.jivesoftware.smackx.jitsimeet.SSRCInfoExtension;
import org.jxmpp.jid.FullJid;

import java.io.IOException;
import java.util.logging.Logger;

import timber.log.Timber;

/**
 * An implementation of a Jingle IQ provider that parses incoming Jingle IQs.
 *
 * @author Florian Schmaus
 * @author Eng Chong Meng
 * @author Emil Ivov
 */
public class JingleProvider extends IqProvider<Jingle>
{
    private static final Logger LOGGER = Logger.getLogger(JingleProvider.class.getName());

    /**
     * Creates a new instance of the <code>JingleProvider</code> and register all jingle related extension providers.
     * It is the responsibility of the application to register the <code>JingleProvider</code> itself.
     *
     * Note: All sub Elements without its own NAMESPACE use their parent NAMESPACE for provider support (Parser implementation)
     */
    public JingleProvider()
    {
        // <description/> provider
        ProviderManager.addExtensionProvider(
                RtpDescription.ELEMENT, RtpDescription.NAMESPACE,
                new DefaultXmlElementProvider<>(RtpDescription.class));

        // <payload-type/> provider
        ProviderManager.addExtensionProvider(
                PayloadType.ELEMENT, RtpDescription.NAMESPACE,
                new DefaultXmlElementProvider<>(PayloadType.class, RtpDescription.NAMESPACE));

        // <parameter/> provider - RtpDescription
        ProviderManager.addExtensionProvider(
                ParameterElement.ELEMENT, RtpDescription.NAMESPACE,
                new DefaultXmlElementProvider<>(ParameterElement.class, RtpDescription.NAMESPACE));

        // <parameter/> provider - RtpHeader
        ProviderManager.addExtensionProvider(
                ParameterElement.ELEMENT, RtpHeader.NAMESPACE,
                new DefaultXmlElementProvider<>(ParameterElement.class, RtpHeader.NAMESPACE));

        // <parameter/> provider - SdpSource
        ProviderManager.addExtensionProvider(
                ParameterElement.ELEMENT, SdpSource.NAMESPACE,
                new DefaultXmlElementProvider<>(ParameterElement.class, SdpSource.NAMESPACE));

        // <rtp-hdrext/> provider
        ProviderManager.addExtensionProvider(
                RtpHeader.ELEMENT, RtpHeader.NAMESPACE,
                new DefaultXmlElementProvider<>(RtpHeader.class));

        // <extmap-allow-mixed/> provider
        ProviderManager.addExtensionProvider(
                RtpExtmap.ELEMENT, RtpExtmap.NAMESPACE,
                new DefaultXmlElementProvider<>(RtpExtmap.class));

        // ice-udp transport - IceUdpTransport
        ProviderManager.addExtensionProvider(
                IceUdpTransport.ELEMENT, IceUdpTransport.NAMESPACE,
                new DefaultXmlElementProvider<>(IceUdpTransport.class));

        // <raw-udp/> provider - RawUdpTransport
        ProviderManager.addExtensionProvider(
                IceUdpTransport.ELEMENT, RawUdpTransport.NAMESPACE,
                new DefaultXmlElementProvider<>(RawUdpTransport.class));

        // ice-udp <candidate/> provider - IceUdpTransportCandidate
        ProviderManager.addExtensionProvider(
                IceUdpTransportCandidate.ELEMENT, IceUdpTransportCandidate.NAMESPACE,
                new DefaultXmlElementProvider<>(IceUdpTransportCandidate.class));

        // raw-udp <candidate/> provider - RawUdpTransport
        ProviderManager.addExtensionProvider(
                IceUdpTransportCandidate.ELEMENT, RawUdpTransport.NAMESPACE,
                new DefaultXmlElementProvider<>(IceUdpTransportCandidate.class));

        // ice-udp <remote-candidate/> provider - IceUdpTransportRemoteCandidate
        ProviderManager.addExtensionProvider(
                IceUdpTransportRemoteCandidate.ELEMENT, IceUdpTransportRemoteCandidate.NAMESPACE,
                new DefaultXmlElementProvider<>(IceUdpTransportRemoteCandidate.class));

        // rtcp-mux => XEP-0167: Jingle RTP Sessions
        ProviderManager.addExtensionProvider(
                RtcpMux.ELEMENT, RtpDescription.NAMESPACE,
                new DefaultXmlElementProvider<>(RtcpMux.class, RtpDescription.NAMESPACE));

        // rtcp-mux =>  Multiplexing RTP Data and Control Packets on a Single Port (April 2010)
        // https://tools.ietf.org/html/rfc5761#section-5.1.3 (5.1.3. Interactions with ICE)
        ProviderManager.addExtensionProvider(
                RtcpMux.ELEMENT, IceUdpTransport.NAMESPACE,
                new DefaultXmlElementProvider<>(RtcpMux.class, IceUdpTransport.NAMESPACE));

        // <encryption/> provider
        ProviderManager.addExtensionProvider(
                SrtpEncryption.ELEMENT, SrtpEncryption.NAMESPACE,
                new DefaultXmlElementProvider<>(SrtpEncryption.class));

        // <zrtp-hash/> provider
        ProviderManager.addExtensionProvider(
                ZrtpHash.ELEMENT, ZrtpHash.NAMESPACE,
                new DefaultXmlElementProvider<>(ZrtpHash.class));

        // <crypto/> provider
        ProviderManager.addExtensionProvider(
                SdpCrypto.ELEMENT, RtpDescription.NAMESPACE,
                new DefaultXmlElementProvider<>(SdpCrypto.class));

        // <bundle/> provider (jitsi-specific)
        ProviderManager.addExtensionProvider(
                BundleExtension.ELEMENT, BundleExtension.NAMESPACE,
                new DefaultXmlElementProvider<>(BundleExtension.class));

        // <group/> provider
        ProviderManager.addExtensionProvider(
                Grouping.ELEMENT, Grouping.NAMESPACE,
                new DefaultXmlElementProvider<>(Grouping.class));

        // Jitsi inputevent <inputevt/> provider
        ProviderManager.addExtensionProvider(
                InputEvent.ELEMENT, InputEvent.NAMESPACE,
                new DefaultXmlElementProvider<>(InputEvent.class));

        // coin <conference-info/> provider
        ProviderManager.addExtensionProvider(
                CoinExtension.ELEMENT, CoinExtension.NAMESPACE,
                new DefaultXmlElementProvider<>(CoinExtension.class));

        // DTLS-SRTP
        ProviderManager.addExtensionProvider(
                SrtpFingerprint.ELEMENT, SrtpFingerprint.NAMESPACE,
                new DefaultXmlElementProvider<>(SrtpFingerprint.class));

        /*
         * XEP-0251: Jingle Session Transfer <transfer/> and <transferred> providers
         */
        ProviderManager.addExtensionProvider(
                SdpTransfer.ELEMENT, SdpTransfer.NAMESPACE,
                new DefaultXmlElementProvider<>(SdpTransfer.class));

        ProviderManager.addExtensionProvider(
                SdpTransferred.ELEMENT, SdpTransferred.NAMESPACE,
                new DefaultXmlElementProvider<>(SdpTransferred.class));

        // conference description <callid/> provider (jitsi-specific)
        ProviderManager.addExtensionProvider(
                CallIdExtension.ELEMENT, ConferenceDescriptionExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(CallIdExtension.class));

        // rtcp-fb
        ProviderManager.addExtensionProvider(
                RtcpFb.ELEMENT, RtcpFb.NAMESPACE,
                new DefaultXmlElementProvider<>(RtcpFb.class));

        //web-socket
        ProviderManager.addExtensionProvider(
                WebSocketExtension.ELEMENT, WebSocketExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(WebSocketExtension.class));

        // ssrcInfo (jitsimeet-specific)
        ProviderManager.addExtensionProvider(
                SSRCInfoExtension.ELEMENT, SSRCInfoExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(SSRCInfoExtension.class));
    }

    /**
     * Parses a Jingle IQ sub-document and returns a {@link Jingle} instance.
     *
     * @param parser an XML parser.
     * @return a new {@link Jingle} instance.
     * @throws IOException, XmlPullParserException, ParseException if an error occurs parsing the XML.
     */
    @Override
    public Jingle parse(XmlPullParser parser, int initialDepth, IqData iqData, XmlEnvironment xmlEnvironment)
            throws XmlPullParserException, IOException, SmackParsingException
    {
        Jingle.Builder builder = Jingle.builder(iqData);

        String actionString = parser.getAttributeValue("", Jingle.ATTR_ACTION);
        if (actionString != null) {
            JingleAction action = JingleAction.fromString(actionString);
            builder.setAction(action);
        }

        FullJid initiator = ParserUtils.getFullJidAttribute(parser, Jingle.ATTR_INITIATOR);
        builder.setInitiator(initiator);

        FullJid responder = ParserUtils.getFullJidAttribute(parser, Jingle.ATTR_RESPONDER);
        builder.setResponder(responder);

        String sessionId = parser.getAttributeValue("", Jingle.ATTR_SESSION_ID);
        builder.setSessionId(sessionId);

        // Sub-elements providers
        DefaultXmlElementProvider<JingleContent> contentProvider = new DefaultXmlElementProvider<>(JingleContent.class);
        DefaultXmlElementProvider<SdpTransfer> transferProvider = new DefaultXmlElementProvider<>(SdpTransfer.class);
        DefaultXmlElementProvider<CoinExtension> coinProvider = new DefaultXmlElementProvider<>(CoinExtension.class);
        DefaultExtensionElementProvider<CallIdExtension> callidProvider = new DefaultExtensionElementProvider<>(CallIdExtension.class);

        outerloop:
        while (true) {
            XmlPullParser.Event eventType = parser.next();
            switch (eventType) {
                case START_ELEMENT:
                    String tagName = parser.getName();
                    String namespace = parser.getNamespace();
                    switch (tagName) {
                        case JingleContent.ELEMENT:
                            // JingleContent content = parseJingleContent(parser, parser.getDepth());
                            JingleContent content = contentProvider.parse(parser);
                            builder.addJingleContent(content);
                            break;

                        case JingleReason.ELEMENT:
                    		JingleReason reason = parseJingleReason(parser);
                            builder.setReason(reason);
                            break;

                        // <transfer/>
                        case SdpTransfer.ELEMENT:
                            if (namespace.equals(SdpTransfer.NAMESPACE)) {
                                builder.addExtension(transferProvider.parse(parser));
                            }
                            break;
                        // <conference-info/>
                        case CoinExtension.ELEMENT:
                            builder.addExtension(coinProvider.parse(parser));
                            break;
                        case CallIdExtension.ELEMENT:
                            builder.addExtension(callidProvider.parse(parser));
                            break;
                        case Grouping.ELEMENT:
                            builder.addExtension(Grouping.parseExtension(parser));
                            break;

                        default:
                            // <mute/> <active/> and other session-info element handlers
                            if (namespace.equals(SessionInfo.NAMESPACE)) {
                                Timber.d("Handle Jingle Session-Info: <%s xml:'%s'>)", tagName, namespace);
                                SessionInfoType type = SessionInfoType.valueOf(tagName);

                                // <mute/> <unmute/>
                                if (type == SessionInfoType.mute || type == SessionInfoType.unmute) {
                                    String name = parser.getAttributeValue("", SessionInfo.ATTR_NAME);
                                    String creator = parser.getAttributeValue("", SessionInfo.ATTR_CREATOR);
                                    builder.setSessionInfo(SessionInfo.builder(type)
                                            .setName(name)
                                            .setCreator(creator)
                                            .build()
                                    );
                                }
                                // <active/>, <hold/>, <unhold/>, and <ringing/> etc.
                                else {
                                    builder.setSessionInfo(SessionInfo.builder(type).build());
                                }
                                break;
                            }
                            else {
                                try {
                                    PacketParserUtils.addExtensionElement(builder.build(), parser, xmlEnvironment);
                                    Timber.e("Unknown jingle element: <%s xml:'%s'>: %s)",
                                            tagName, namespace, builder.build());
                                } catch (XmlPullParserException e) {
                                    // Exception if not supported by addExtensionElement, Just log info
                                    Timber.e("AddExtensionElement Exception: %s", builder.build().toXML());
                                }
                            }
                            // LOGGER.severe("Unknown Jingle element: " + tagName);
                            break;
                    }
                    break;
                case END_ELEMENT:
                    if (parser.getDepth() == initialDepth) {
                        break outerloop;
                    }
                    break;
                default:
                    // Catch all for incomplete switch (MissingCasesInEnumSwitch) statement.
                    break;
            }
        }
        return builder.build();
    }

    public static JingleContent parseJingleContent(XmlPullParser parser, final int initialDepth)
            throws XmlPullParserException, IOException, SmackParsingException
    {
        JingleContent.Builder builder = JingleContent.builder();

        String creatorString = parser.getAttributeValue("", JingleContent.ATTR_CREATOR);
        JingleContent.Creator creator = JingleContent.Creator.valueOf(creatorString);
        builder.setCreator(creator);

        String disposition = parser.getAttributeValue("", JingleContent.ATTR_DISPOSITION);
        builder.setDisposition(disposition);

        String name = parser.getAttributeValue("", JingleContent.ATTR_NAME);
        builder.setName(name);

        String sendersString = parser.getAttributeValue("", JingleContent.ATTR_SENDERS);
        if (sendersString != null) {
            JingleContent.Senders senders = JingleContent.Senders.valueOf(sendersString);
            builder.setSenders(senders);
        }

        outerloop:
        while (true) {
            XmlPullParser.Event eventType = parser.next();
            switch (eventType) {
                case START_ELEMENT:
                    String tagName = parser.getName();
                    String namespace = parser.getNamespace();
                    switch (tagName) {
                        case JingleContentDescription.ELEMENT: {
                            JingleContentDescription description;
                            JingleContentDescriptionProvider<?> provider = JingleContentProviderManager.getJingleContentDescriptionProvider(namespace);
                            if (provider == null) {
                                StandardExtensionElement standardExtensionElement = StandardExtensionElementProvider.INSTANCE.parse(parser);
                                description = new UnknownJingleContentDescription(standardExtensionElement);
                            }
                            else {
                                description = provider.parse(parser);
                            }
                            builder.addChildElement(description);
                            break;
                        }
                        case JingleContentTransport.ELEMENT: {
                            JingleContentTransport transport;
                            JingleContentTransportProvider<?> provider = JingleContentProviderManager.getJingleContentTransportProvider(namespace);
                            if (provider == null) {
                                StandardExtensionElement standardExtensionElement = StandardExtensionElementProvider.INSTANCE.parse(parser);
                                transport = new UnknownJingleContentTransport(standardExtensionElement);
                            }
                            else {
                                transport = provider.parse(parser);
                            }
                            builder.addChildElement(transport);
                            break;
                        }
                        default:
                            LOGGER.severe("Unknown Jingle content element: " + tagName);
                            break;
                    }
                    break;
                case END_ELEMENT:
                    if (parser.getDepth() == initialDepth) {
                        break outerloop;
                    }
                    break;
                default:
                    // Catch all for incomplete switch (MissingCasesInEnumSwitch) statement.
                    break;
            }
        }

        return builder.build();
    }

    public static JingleReason parseJingleReason(XmlPullParser parser)
                    throws XmlPullParserException, IOException, SmackParsingException {
        ParserUtils.assertAtStartTag(parser);
        final int initialDepth = parser.getDepth();
        final String jingleNamespace = parser.getNamespace();

        JingleReason.Reason reason = null;
        ExtensionElement element = null;
        String text = null;

        // 'sid' is only set if the reason is 'alternative-session'.
        String sid = null;

        outerloop: while (true) {
            XmlPullParser.TagEvent event = parser.nextTag();
            switch (event) {
            case START_ELEMENT:
                String elementName = parser.getName();
                String namespace = parser.getNamespace();
                if (namespace.equals(jingleNamespace)) {
                    switch (elementName) {
                    case "text":
                        text = parser.nextText();
                        break;
                    case "alternative-session":
                        parser.next();
                        sid = parser.nextText();
                        break;
                    default:
                        reason = Reason.fromString(elementName);
                        break;
                    }
                } else {
                    element = PacketParserUtils.parseExtensionElement(elementName, namespace, parser, null);
                }
                break;
            case END_ELEMENT:
                if (parser.getDepth() == initialDepth) {
                    break outerloop;
                }
                break;
            }
        }

        JingleReason res;
        if (sid != null) {
            res = new JingleReason.AlternativeSession(sid, text, element);
        } else {
            res = new JingleReason(reason, text, element);
        }
        return res;
    }
}
