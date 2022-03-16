/*
 * Copyright 2017-2012 Jive Software
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
package org.jivesoftware.smackx.jingle;

import static org.jivesoftware.smack.xml.XmlPullParser.Event.END_ELEMENT;

import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jivesoftware.smackx.AbstractXmlElement;
import org.jivesoftware.smackx.DefaultXmlElementProvider;
import org.jivesoftware.smackx.jingle.element.JingleContent;

import java.io.IOException;
import java.util.List;

import javax.xml.namespace.QName;

/**
 * Jingle group packet extension.
 * @see <a href="https://xmpp.org/extensions/xep-0338.html">XEP-0338: Jingle Grouping Framework 1.0.0 (2020-07-21)</a>
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class Grouping extends AbstractXmlElement
{
    /**
     * The name of the "group" element.
     */
    public static final String ELEMENT = "group";

    /**
     * The namespace for the "grouping" element.
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:apps:grouping:0";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * The name of the payload <code>id</code> SDP argument.
     */
    public static final String ATTR_SEMANTICS = "semantics";

    /**
     * Name of the "bundle" semantics.
     */
    public static final String SEMANTICS_BUNDLE = "BUNDLE";

    public Grouping()
    {
        super(builder());
    }

    /**
     * Creates a new <code>Grouping</code>; required by DefaultXmlElementProvider()
     */
    public Grouping(Builder build)
    {
        super(build);
    }

    /**
     * Gets the semantics of this group.
     *
     * @return the semantics of this group.
     */
    public String getSemantics()
    {
        return getAttributeValue(ATTR_SEMANTICS);
    }

    /**
     * Gets the contents of this group.
     *
     * @return the contents of this group.
     */
    public List<JingleContent> getContents()
    {
        return getChildElements(JingleContent.class);
    }

    /**
     * Creates new <code>Grouping</code> for BUNDLE semantics initialized with given <code>contents</code> list.
     *
     * @param contents the list that contains the contents to be bundled.
     * @return new <code>Grouping</code> for BUNDLE semantics initialized with given <code>contents</code> list.
     */
    public static Grouping createBundleGroup(List<JingleContent> contents)
    {
        Grouping.Builder builder = Grouping.builder()
                .setSemantics(SEMANTICS_BUNDLE)
                .addContents(contents);
        return builder.build();
    }

    public static Grouping.Builder builder()
    {
        return new Builder(ELEMENT, NAMESPACE);
    }

    /**
     * Builder for Grouping. Use {@link AbstractXmlElement#builder()} to
     * obtain a new instance and {@link #build} to build the Grouping.
     */
    public static final class Builder extends AbstractXmlElement.Builder<Builder, Grouping>
    {
        protected Builder(String element, String namespace)
        {
            super(element, namespace);
        }

        /**
         * Sets the semantics of this group.
         */
        public Builder setSemantics(String semantics)
        {
            addAttribute(ATTR_SEMANTICS, semantics);
            return this;
        }

        /**
         * Sets the contents of this group. For each content from given <code>contents</code>list only its
         * name is being preserved.
         *
         * @param contents the contents of this group.
         */
        public Builder addContents(List<JingleContent> contents)
        {
            for (JingleContent content : contents) {
                JingleContent.Builder contentBuilder = JingleContent.builder();
                contentBuilder.setName(content.getName());
                addChildElement(contentBuilder.build());
            }
            return this;
        }

        @Override
        public Grouping build()
        {
            return new Grouping(this);
        }

        @Override
        public Builder getThis()
        {
            return this;
        }
    }

    /**
     * Parses group extension content.
     *
     * @param parser an XML parser positioned at the packet's starting element.
     * @return new <code>Grouping</code> initialized with parsed contents list.
     * @throws IOException, XmlPullParserException, ParseException if an error occurs parsing the XML.
     */
    public static Grouping parseExtension(XmlPullParser parser)
            throws IOException, XmlPullParserException, SmackParsingException
    {
        DefaultXmlElementProvider<JingleContent> contentProvider = new DefaultXmlElementProvider<>(JingleContent.class);

        Grouping.Builder builder = Grouping.builder();
        String semantics = parser.getAttributeValue("", ATTR_SEMANTICS);
        if (semantics != null)
            builder.setSemantics(semantics);

        boolean done = false;
        while (!done) {
            XmlPullParser.Event eventType = parser.next();
            String name = parser.getName();

            if (name.equals(JingleContent.ELEMENT)) {
                JingleContent content = contentProvider.parse(parser);
                builder.addChildElement(content);
            }

            if ((eventType == END_ELEMENT) && parser.getName().equals(ELEMENT)) {
                done = true;
            }
        }
        return builder.build();
    }
}