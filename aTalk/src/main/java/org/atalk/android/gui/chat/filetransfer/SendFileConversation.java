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
package org.atalk.android.gui.chat.filetransfer;

import android.view.*;

import net.java.sip.communicator.service.protocol.FileTransfer;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusListener;

import org.atalk.android.*;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatFragment;

import java.io.File;
import java.util.Calendar;

/**
 * The <tt>SendFileConversationComponent</tt> is the component added in the chat conversation
 * when user sends a file.
 *
 * @author Eng Chong Meng
 */
public class SendFileConversation extends FileTransferConversation implements FileTransferStatusListener
{
    // private final FileTransfer fileTransfer;
    private String mSendTo;
    private String mDate;
    private boolean mStickMode;

    public SendFileConversation()
    {
    }

    /**
     * Creates a <tt>SendFileConversationComponent</tt> by specifying the parent chat panel, where
     * this component is added, the destination contact of the transfer and file to transfer.
     *
     * @param cPanel the parent chat panel, where this view component is added
     * @param sendTo the name of the destination contact
     * @param fileName the file to transfer
     */

    public static SendFileConversation newInstance(ChatFragment cPanel, String sendTo, final String fileName,
            boolean stickerMode)
    {
        SendFileConversation fragmentSFC = new SendFileConversation();
        fragmentSFC.mChatFragment = cPanel;
        fragmentSFC.mSendTo = sendTo;
        fragmentSFC.mXferFile = new File(fileName);
        fragmentSFC.mDate = Calendar.getInstance().getTime().toString();
        fragmentSFC.mStickMode = stickerMode;
        return fragmentSFC;
    }

    public View SendFileConversationForm(LayoutInflater inflater, ChatFragment.MessageViewHolder msgViewHolder,
            ViewGroup container, int id, boolean init)
    {
        msgId = id;
        View convertView = inflateViewForFileTransfer(inflater, msgViewHolder, container, init);

        messageViewHolder.arrowDir.setImageResource(R.drawable.filexferarrowout);
        MyGlideApp.loadImage(messageViewHolder.stickerView, mXferFile, false);

        this.setCompletedDownloadFile(mChatFragment, mXferFile);
        messageViewHolder.titleLabel.setText(aTalkApp.getResString(R.string.xFile_FILE_WAITING_TO_ACCEPT, mDate, mSendTo));
        messageViewHolder.fileLabel.setText(getFileLabel(mXferFile));

        messageViewHolder.cancelButton.setVisibility(View.VISIBLE);
        messageViewHolder.retryButton.setVisibility(View.GONE);
        messageViewHolder.retryButton.setOnClickListener(v -> {
            messageViewHolder.retryButton.setVisibility(View.GONE);
            mChatFragment.new SendFile(mXferFile, SendFileConversation.this, msgId, mStickMode).execute();
        });

		/* Must track file transfer status as Android will request view redraw on listView
		scrolling, new message send or received */
        int status = getXferStatus();
        if (status == -1) {
            mChatFragment.new SendFile(mXferFile, SendFileConversation.this, msgId, mStickMode).execute();
        }
        else {
            updateView(status);
        }
        return convertView;
    }

    /**
     * Handles file transfer status changes. Updates the interface to reflect the changes.
     */
    private void updateView(final int status)
    {
        boolean bgAlert = false;
        switch (status) {
            case FileTransferStatusChangeEvent.PREPARING:
                messageViewHolder.titleLabel
                        .setText(aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_PREPARING, mDate, mSendTo));
                break;

            case FileTransferStatusChangeEvent.IN_PROGRESS:
                if (!messageViewHolder.mProgressBar.isShown()) {
                    messageViewHolder.mProgressBar.setVisibility(View.VISIBLE);
                    messageViewHolder.mProgressBar.setMax((int) mXferFile.length());
                }
                messageViewHolder.titleLabel.setText(aTalkApp.getResString(R.string.xFile_FILE_SENDING_TO, mDate, mSendTo));
                break;

            case FileTransferStatusChangeEvent.COMPLETED:
                messageViewHolder.titleLabel.setText(aTalkApp.getResString(R.string.xFile_FILE_SEND_COMPLETED, mDate, mSendTo));
                messageViewHolder.cancelButton.setVisibility(View.GONE);
                break;

            // not offer to retry - smack replied as failed when recipient rejects on some devices
            case FileTransferStatusChangeEvent.FAILED:
                messageViewHolder.titleLabel.setText(aTalkApp.getResString(R.string.xFile_FILE_UNABLE_TO_SEND, mDate, mSendTo));
                messageViewHolder.cancelButton.setVisibility(View.GONE);
                // messageViewHolder.retryButton.setVisibility(View.VISIBLE);
                bgAlert = true;
                break;

            case FileTransferStatusChangeEvent.CANCELED:
                messageViewHolder.titleLabel.setText(aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_CANCELED, mDate));
                messageViewHolder.cancelButton.setVisibility(View.GONE);
                bgAlert = true;
                break;

            case FileTransferStatusChangeEvent.REFUSED:
                messageViewHolder.titleLabel.setText(aTalkApp.getResString(R.string.xFile_FILE_SEND_REFUSED, mDate, mSendTo));
                messageViewHolder.retryButton.setVisibility(View.GONE);
                messageViewHolder.cancelButton.setVisibility(View.GONE);
                bgAlert = true;
                break;
        }
        if (bgAlert) {
            messageViewHolder.titleLabel.setTextColor(AndroidGUIActivator.getResources().getColor("red"));
        }
    }

    /**
     * Handles file transfer status changes. Updates the interface to reflect the changes.
     */
    public void statusChanged(final FileTransferStatusChangeEvent event)
    {
        final FileTransfer fileTransfer = event.getFileTransfer();
        final int status = event.getNewStatus();
        setXferStatus(status);

        // Must execute in UiThread to Update UI information
        runOnUiThread(() -> {
            updateView(status);
            if (status == FileTransferStatusChangeEvent.COMPLETED
                    || status == FileTransferStatusChangeEvent.CANCELED
                    || status == FileTransferStatusChangeEvent.FAILED
                    || status == FileTransferStatusChangeEvent.REFUSED) {
                // must do this in UI, otherwise the status is not being updated to FileRecord
                if (fileTransfer != null)
                    fileTransfer.removeStatusListener(SendFileConversation.this);
                // removeProgressListener();
            }
        });
    }

    /**
     * Sets the <tt>FileTransfer</tt> object received from the protocol and corresponding to the
     * file transfer process associated with this panel.
     *
     * @param fileTransfer the <tt>FileTransfer</tt> object associated with this panel
     */
    public void setProtocolFileTransfer(FileTransfer fileTransfer)
    {
        // activate File History service to keep track of the progress - need more work if want to keep sending history.
        // fileTransfer.addStatusListener(new FileHistoryServiceImpl());

        this.fileTransfer = fileTransfer;
        fileTransfer.addStatusListener(this);
        this.setFileTransfer(fileTransfer, mXferFile.length());
    }

    /**
     * Change the xfer file status, must execute in UI thread to call this.
     */
    public void setStatus(final int status)
    {
        setXferStatus(status);
        // Must execute in UiThread to Update UI information
        runOnUiThread(() -> {
            updateView(status);
        });
    }

    /**
     * Returns the label to show on the progress bar.
     *
     * @param bytesString the bytes that have been transferred
     * @return the label to show on the progress bar
     */
    @Override
    protected String getProgressLabel(String bytesString)
    {
        return aTalkApp.getResString(R.string.xFile_FILE_BYTE_SENT, bytesString);
    }

    @Override
    protected void setXferStatus(int xferStatus)
    {
        mChatFragment.getChatListAdapter().setXferStatus(msgId, xferStatus);
    }
}
