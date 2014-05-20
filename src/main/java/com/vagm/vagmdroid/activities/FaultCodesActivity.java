package com.vagm.vagmdroid.activities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import roboguice.inject.InjectView;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.inject.Inject;
import com.vagm.vagmdroid.R;
import com.vagm.vagmdroid.enums.FunctionCode;
import com.vagm.vagmdroid.exceptions.ControllerCommunicationException;
import com.vagm.vagmdroid.exceptions.ControllerWrongResponseException;
import com.vagm.vagmdroid.service.BluetoothService.ServiceCommand;
import com.vagm.vagmdroid.service.BufferService;

/**
 * The Class FaultCodesActivity.
 * @author roman_konovalov
 */
public class FaultCodesActivity extends CustomAbstractActivity implements OnClickListener {

	/**
	 * LOG.
	 */
	private static final Logger LOG = LoggerFactory.getLogger(FaultCodesActivity.class);

	/**
	 * bufferService.
	 */
	@Inject
	private BufferService bufferService;

	/**
	 * faultCodes.
	 */
	@InjectView(R.id.faultCodes)
	private TextView faultCodes;

	/**
	 * The Handler that gets information back from the BluetoothService.
	 */
	@SuppressLint("HandlerLeak")
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(final Message msg) {
			super.handleMessage(msg);
			final ServiceCommand serviceCommand = ServiceCommand.values()[msg.what];
			if (serviceCommand == ServiceCommand.MESSAGE_READ) {
				byte[] message = (byte[]) msg.obj;
				LOG.trace("Recieved message from conroller: {}", bufferService.bytesToHex(message));
				try {
					proceedMessage(message);
				} catch (final ControllerCommunicationException e) {
					getControllerNotAnswerAlert().show();
				} catch (ControllerWrongResponseException e) {
					LOG.warn("Wrong response", e);
				}
			} else if (serviceCommand == ServiceCommand.CONNECTION_LOST) {
				Toast.makeText(getApplicationContext(), getText(R.string.connection_lost), Toast.LENGTH_SHORT).show();
				finish();
			}
		}
	};

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LOG.debug("onCreate");
		setContentView(R.layout.activity_fault_codes);
		setButtonOnClickListner((ViewGroup) findViewById(R.id.measBlocksLayout), this);

	}

	/**
	 * proceedMessage.
	 * @param message
	 *            message
	 * @throws ControllerCommunicationException
	 *             if some communication error occurs
	 * @throws ControllerWrongResponseException
	 *             if wrong response from controller occurs
	 */
	private void proceedMessage(final byte[] message) throws ControllerCommunicationException, ControllerWrongResponseException {
		String error = bufferService.getFaultCodesInfo(message);
		faultCodes.append(error);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.fault_codes, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Handler getHandler() {
		return mHandler;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onClick(final View v) {
		switch (v.getId()) {
		case R.id.bFaultCodesBack:
			LOG.debug("Exiting FaultCodes Activity");
			finish();
			break;

		case R.id.bClearCodes:
			bluetoothService.write(FunctionCode.CLEAR_CODES.getCode());
			break;

		default:
			break;
		}
	}

}
