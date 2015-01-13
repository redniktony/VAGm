package com.vagm.vagmdroid.service;

import static com.vagm.vagmdroid.constants.VAGmConstans.CONTROLLER_NOT_FOUND;
import static com.vagm.vagmdroid.constants.VAGmConstans.CONTROLLER_NO_ANSWER;
import static com.vagm.vagmdroid.util.NumberUtil.byteToInt;

import javax.inject.Singleton;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;

import com.google.inject.Inject;
import com.vagm.vagmdroid.R;
import com.vagm.vagmdroid.constants.VAGmConstans;
import com.vagm.vagmdroid.dto.DataStreamDTO;
import com.vagm.vagmdroid.enums.AdapterLogKey;
import com.vagm.vagmdroid.exceptions.ControllerCommunicationException;
import com.vagm.vagmdroid.exceptions.ControllerNotFoundException;
import com.vagm.vagmdroid.exceptions.ControllerWrongResponseException;

/**
 * The Class BufferService.
 * 
 * @author Roman_Konovalov
 */
@Singleton
public class BufferService {

    private static final int ECU_FREQUENCY = 8000000;

    private static final String DASH = " - ";

    private static final String ADAPTER_LOG_DELEMITER = ", ";

    private static final String TWO_DIGIT_HEX_FORMAT = "0x%02x";
    
    private static final String FOUR_DIGIT_HEX_FORMAT = "0x%02x%02x";

    /**
     * LOG.
     */
    private static final Logger LOG = LoggerFactory.getLogger(BufferService.class);

    /**
     * dataStreamService.
     */
    @Inject
    private GroupDataService groupDataService;

    /**
     * context.
     */
    @Inject
    private Context context;

    /**
     * faultCodesService.
     */
    @Inject
    private FaultCodesService faultCodesService;

    /**
     * Constructor.
     */
    public BufferService() {
    }

    /**
     * gets ControllerInfo.
     * 
     * @param buffer
     *            buffer
     * @param controllerInfo
     *            controllerInfo
     * @return ControllerInfo
     * @throws ControllerWrongResponseException
     *             if wrong response from controller occurs
     * @throws ControllerNotFoundException
     */
    public String[] getControllerInfo(final byte[] buffer, final String[] controllerInfo) throws ControllerWrongResponseException {
        final String[] result = controllerInfo;
        if (buffer.length == 4) {
            result[0] = String.valueOf(ECU_FREQUENCY / Integer.parseInt(
                    Integer.toHexString(byteToInt(buffer[0])) + Integer.toHexString(byteToInt(buffer[1])), 16));
        } else if (buffer.length > 4) {

            int response = byteToInt(buffer[0]);
            int dataStartPosition = 1;
            if (response <= 0x01) {
                response = byteToInt(buffer[1]);
                dataStartPosition = 2;
            }

            checkResponseCode(VAGmConstans.VAG_BTI_INFO_RES, response);

            if (result[1].length() == 0 && byteToInt(buffer[dataStartPosition]) > 0x80) {
                buffer[dataStartPosition] = (byte) (buffer[dataStartPosition] + 0x80);
            }

            final StringBuilder builder = new StringBuilder();
            for (int i = dataStartPosition; i < buffer.length; i++) {
                if ((buffer[i] >= 0x20) && (buffer[i] <= 0x7E)) {
                    builder.append((char) buffer[i]);
                }
            }

            String resultString = builder.toString();
            int requaredEcuLenth = (VAGmConstans.ECU_LENGTH - result[1].length()) > resultString.length() ? resultString.length()
                    : VAGmConstans.ECU_LENGTH - result[1].length();
            result[1] = result[1] + resultString.substring(0, requaredEcuLenth);
            result[2] = result[2] + resultString.substring(requaredEcuLenth);

        }

        return result;
    }

    /**
     * getMeasBlocksInfo.
     * 
     * @param buffer
     *            buffer
     * @return DataStreamDTO array
     * @throws ControllerWrongResponseException
     *             if wrong response from controller occurs
     * @throws ControllerNotFoundException
     */
    public DataStreamDTO[] getMeasBlocksInfo(final byte[] buffer) throws ControllerWrongResponseException {

        int responseCode = byteToInt(buffer[0]);
        if (responseCode == VAGmConstans.VAG_BTI_ERROR) {
            LOG.trace("No data for current group");
            return new DataStreamDTO[] { DataStreamDTO.getDefault(context), DataStreamDTO.getDefault(context), DataStreamDTO.getDefault(context),
                    DataStreamDTO.getDefault(context) };
        }

        checkResponseCode(VAGmConstans.VAG_BTI_GROUP_RES, responseCode);
        if ((buffer.length - 1) % 3 != 0) {
            throw new ControllerWrongResponseException("Wrong response length from controller: expected multiplicity of three, but was: "
                    + (buffer.length - 1));
        }

        int groupsCount = (buffer.length - 1) / 3;

        DataStreamDTO[] dtos = new DataStreamDTO[4];
        for (int i = 0; i < groupsCount; i++) {
            dtos[i] = groupDataService.encodeGroupData(byteToInt(buffer[i * 3 + 1]), byteToInt(buffer[i * 3 + 2]), byteToInt(buffer[i * 3 + 3]));
        }
        for (int i = groupsCount; i < 4; i++) {
            dtos[i] = DataStreamDTO.getDefault(context);
        }

        return dtos;
    }

    /**
     * getFaultCodesInfo.
     * 
     * @param buffer
     *            buffer
     * @return FaultCodesInfo
     * @throws ControllerWrongResponseException
     *             if wrong response from controller occurs
     * @throws ControllerNotFoundException
     */
    public String getFaultCodesInfo(final byte[] buffer) throws ControllerWrongResponseException {

        int responseCode = byteToInt(buffer[0]);
        if (responseCode == VAGmConstans.VAG_BTI_ERROR) {
            LOG.trace("Error");
            // TODO
            return "ERROR";
        }
        checkResponseCode(VAGmConstans.VAG_BTI_DTC_RES, responseCode);
        if ((buffer.length - 1) % 3 != 0) {
            throw new ControllerWrongResponseException("Wrong response length from controller: expected multiplicity of three, but was: "
                    + (buffer.length - 1));
        }
        if ((byteToInt(buffer[1]) == 0xFF) && (byteToInt(buffer[2]) == 0xFF)) {
            return context.getString(R.string.no_errors);
        }

        StringBuffer result = new StringBuffer();
        for (int i = 0; i < buffer.length / 3; i++) {
            int errorCode = Integer.parseInt(Integer.toHexString(byteToInt(buffer[i * 3 + 1])) + Integer.toHexString(byteToInt(buffer[i * 3 + 2])),
                    16);
            String errorString = faultCodesService.getDTC(errorCode);
            int errorTypeInt = byteToInt(buffer[i * 3 + 3]);
            if (errorTypeInt > 0x80) {
                errorTypeInt = errorTypeInt - 0x80;
            }
            String errorType = faultCodesService.getErrorType(errorTypeInt);
            result.append(errorCode);
            result.append(" ");
            result.append(errorString);
            result.append(DASH);
            result.append(errorType);
            result.append(String.format("%n").intern());
        }
        return result.toString();
    }

    /**
     * getOutputTestsInfo.
     * 
     * @param buffer
     *            buffer
     * @return FaultCodesInfo
     * @throws ControllerWrongResponseException
     *             if wrong response from controller occurs
     * @throws ControllerNotFoundException
     */
    public String getOutputTestsInfo(final byte[] buffer) throws ControllerWrongResponseException {

        int responseCode = byteToInt(buffer[0]);
        if (responseCode == VAGmConstans.VAG_BTI_ERROR) {
            LOG.trace("Error");
            // TODO
            return "ERROR";
        }
        if (responseCode == VAGmConstans.VAG_BTI_ACT_RES_END) {
            return context.getString(R.string.test_ended);
        }
        checkResponseCode(VAGmConstans.VAG_BTI_ACT_RES, responseCode);

        int outputTestCode = Integer.parseInt(Integer.toHexString(byteToInt(buffer[1])) + Integer.toHexString(byteToInt(buffer[2])), 16);
        return faultCodesService.getDTC(outputTestCode);
    }

    /**
     * encodeAdapterLog.
     * 
     * @param adapterLog
     * @return
     * @throws ControllerWrongResponseException
     */
    public String encodeAdapterLog(byte[] adapterLog) {

        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 1; i < adapterLog.length; i++) {
            byte st = adapterLog[i];
            AdapterLogKey logKey = AdapterLogKey.getAdapterLogKey(st);
            stringBuilder.append(logKey.getValue());

            switch (logKey) {
                case GETCHAR_TIMEOUT_ERROR:
                    break;
    
                case BAUDRATE_TICKS:
                    stringBuilder.append(DASH);
                    for (int j = 0; j < 9; j++) {
                        stringBuilder.append(String.format(FOUR_DIGIT_HEX_FORMAT, adapterLog[++i], adapterLog[++i]));
                        if (j != 8) {
                            stringBuilder.append(ADAPTER_LOG_DELEMITER);
                        }
                    }
                    break;
    
                default:
                    stringBuilder.append(DASH);
                    stringBuilder.append(String.format(TWO_DIGIT_HEX_FORMAT, adapterLog[++i]));
                    break;
            }

            if (i != adapterLog.length - 1) {
                stringBuilder.append(String.format("%n").intern());
            }
        }

        return stringBuilder.toString();
    }

    /**
     * Checks AdapterErrors.
     * 
     * @param buffer
     *            buffer
     * @throws ControllerCommunicationException
     *             if some communication error occurs
     * @throws ControllerNotFoundException
     */
    public void checkAdapterErrors(final byte[] buffer) throws ControllerCommunicationException, ControllerNotFoundException {
        if (buffer.length == 1) {
            if (buffer[0] == CONTROLLER_NOT_FOUND) {
                throw new ControllerNotFoundException();
            }
            if (buffer[0] == CONTROLLER_NO_ANSWER) {
                throw new ControllerCommunicationException();
            }
        }
    }

    /**
     * checkResponseCode.
     * 
     * @param expectedCode
     *            expectedCode
     * @param actualCode
     *            actualCode
     * @throws ControllerWrongResponseException
     *             if wrong response from controller occurs
     */
    private void checkResponseCode(final int expectedCode, final int actualCode) throws ControllerWrongResponseException {
        if (expectedCode != actualCode) {
            throw new ControllerWrongResponseException("Wrong response from controller: expected " + String.format(TWO_DIGIT_HEX_FORMAT, expectedCode)
                    + ", but was: " + String.format(TWO_DIGIT_HEX_FORMAT, actualCode));
        }
    }

}
