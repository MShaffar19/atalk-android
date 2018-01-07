/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallState;
import net.java.sip.communicator.service.protocol.event.CallChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallChangeListener;
import net.java.sip.communicator.service.protocol.event.CallPeerEvent;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.R;
import org.atalk.service.osgi.OSGiActivity;

/**
 * The <tt>ReceivedCallActivity</tt> is the activity that corresponds to the screen shown on incoming call.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 */
public class ReceivedCallActivity extends OSGiActivity implements CallChangeListener
{
    /**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(ReceivedCallActivity.class);

    /**
     * The identifier of the call.
     */
    private String callIdentifier;

    /**
     * The corresponding call.
     */
    private Call call;

    /**
     * {@inheritDoc}
     */
    public void onAttachedToWindow()
    {
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        + WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        + WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        + WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
    }

    /**
     * Called when the activity is starting. Initializes the call identifier.
     *
     * @param savedInstanceState
     *         If the activity is being re-initialized after previously being shut down then this Bundle contains the
     *         data it most recently supplied in onSaveInstanceState(Bundle). Note: Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.received_call);
        TextView displayNameView = (TextView) findViewById(R.id.calleeDisplayName);
        TextView addressView = (TextView) findViewById(R.id.calleeAddress);
        ImageView avatarView = (ImageView) findViewById(R.id.calleeAvatar);
        Bundle extras = getIntent().getExtras();
        displayNameView.setText(extras.getString(CallManager.CALLEE_DISPLAY_NAME));
        addressView.setText(extras.getString(CallManager.CALLEE_ADDRESS));

        byte[] avatar = extras.getByteArray(CallManager.CALLEE_AVATAR);
        if (avatar != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(avatar, 0, avatar.length);
            avatarView.setImageBitmap(bitmap);
        }

        callIdentifier = extras.getString(CallManager.CALL_IDENTIFIER);
        call = CallManager.getActiveCall(callIdentifier);
        if (call == null) {
            logger.error("There is no call with ID: " + callIdentifier);
            finish();
            return;
        }

        ImageView hangupView = (ImageView) findViewById(R.id.hangupButton);
        hangupView.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                hangupCall();
            }
        });

        final ImageView callButton = (ImageView) findViewById(R.id.callButton);
        callButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                answerCall(call, false);
            }
        });
    }

    /**
     * Method mapped to answer button's onClick event
     *
     * @param v
     *         the answer with video button's <tt>View</tt>
     */
    public void onAnswerWithVideoClicked(View v)
    {
        if (call != null) {
            logger.trace("Answer call with video");
            answerCall(call, true);
        }
    }

    /**
     * Answers the given call and launches the call user interface.
     *
     * @param call
     *         the call to answer
     * @param isVideoCall
     *         indicates if video shall be used
     */
    private void answerCall(final Call call, boolean isVideoCall)
    {
        CallManager.answerCall(call, isVideoCall);
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                Intent videoCall = VideoCallActivity.createVideoCallIntent(ReceivedCallActivity.this, callIdentifier);
                startActivity(videoCall);
                finish();
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onResume()
    {
        super.onResume();

        if (call.getCallState().equals(CallState.CALL_ENDED)) {
            finish();
        }
        else {
            call.addCallChangeListener(this);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPause()
    {
        if (call != null) {
            call.removeCallChangeListener(this);
        }
        super.onPause();
    }

    /**
     * Hangs up the call and finishes this <tt>Activity</tt>.
     */
    private void hangupCall()
    {
        CallManager.hangupCall(call);
        finish();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Hangs up the call when back is pressed as this Activity will be not displayed again.
            hangupCall();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Indicates that a new call peer has joined the source call.
     *
     * @param evt
     *         the <tt>CallPeerEvent</tt> containing the source call and call peer.
     */
    public void callPeerAdded(CallPeerEvent evt)
    {

    }

    /**
     * Indicates that a call peer has left the source call.
     *
     * @param evt
     *         the <tt>CallPeerEvent</tt> containing the source call and call peer.
     */
    public void callPeerRemoved(CallPeerEvent evt)
    {

    }

    /**
     * Indicates that a change has occurred in the state of the source call.
     *
     * @param evt
     *         the <tt>CallChangeEvent</tt> instance containing the source calls and its old and new state.
     */
    public void callStateChanged(CallChangeEvent evt)
    {
        if (evt.getNewValue().equals(CallState.CALL_ENDED)) {
            finish();
        }
    }
}
