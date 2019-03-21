package io.mosip.registration.processor.print.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.mosip.kernel.core.cbeffutil.spi.CbeffUtil;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.pdfgenerator.exception.PDFGeneratorException;
import io.mosip.kernel.core.qrcodegenerator.exception.QrcodeGenerationException;
import io.mosip.kernel.core.qrcodegenerator.spi.QrCodeGenerator;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.kernel.pdfgenerator.itext.constant.PDFGeneratorExceptionCodeConstant;
import io.mosip.kernel.qrcode.generator.zxing.constant.QrVersion;
import io.mosip.registration.processor.core.code.ApiName;
import io.mosip.registration.processor.core.code.EventId;
import io.mosip.registration.processor.core.code.EventName;
import io.mosip.registration.processor.core.code.EventType;
import io.mosip.registration.processor.core.constant.IdType;
import io.mosip.registration.processor.core.constant.LoggerFileConstant;
import io.mosip.registration.processor.core.constant.UinCardType;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.TemplateProcessingFailureException;
import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.packet.dto.Identity;
import io.mosip.registration.processor.core.packet.dto.demographicinfo.JsonValue;
import io.mosip.registration.processor.core.spi.packetmanager.PacketInfoManager;
import io.mosip.registration.processor.core.spi.print.service.PrintService;
import io.mosip.registration.processor.core.spi.restclient.RegistrationProcessorRestClientService;
import io.mosip.registration.processor.core.spi.uincardgenerator.UinCardGenerator;
import io.mosip.registration.processor.core.util.CbeffToBiometricUtil;
import io.mosip.registration.processor.core.util.JsonUtil;
import io.mosip.registration.processor.core.util.exception.FieldNotFoundException;
import io.mosip.registration.processor.core.util.exception.InstantanceCreationException;
import io.mosip.registration.processor.message.sender.template.TemplateGenerator;
import io.mosip.registration.processor.packet.storage.dto.ApplicantInfoDto;
import io.mosip.registration.processor.packet.storage.exception.IdentityNotFoundException;
import io.mosip.registration.processor.packet.storage.exception.ParsingException;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.print.service.dto.JsonFileDTO;
import io.mosip.registration.processor.print.service.dto.JsonRequestDTO;
import io.mosip.registration.processor.print.service.exception.IDRepoResponseNull;
import io.mosip.registration.processor.print.service.exception.UINNotFoundInDatabase;
import io.mosip.registration.processor.print.service.kernel.dto.Documents;
import io.mosip.registration.processor.print.service.kernel.dto.IdResponseDTO;
import io.mosip.registration.processor.print.service.utility.UINCardConstant;
import io.mosip.registration.processor.rest.client.audit.builder.AuditLogRequestBuilder;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.service.RegistrationStatusService;

/**
 * The Class PrintServiceImpl.
 * 
 * @author M1048358 Alok
 */
@Service
public class PrintServiceImpl implements PrintService<Map<String, byte[]>> {

	/** The Constant TXT. */
	private static final String TXT = ".txt";

	/** The Constant FILE_SEPARATOR. */
	public static final String FILE_SEPARATOR = File.separator;

	/** The Constant LANGUAGE. */
	private static final String LANGUAGE = "language";

	/** The Constant VALUE. */
	private static final String VALUE = "value";

	/** The primary lang. */
	@Value("${mosip.primary-language}")
	private String primaryLang;

	/** The secondary lang. */
	@Value("${mosip.secondary-language}")
	private String secondaryLang;

	/** The Constant UIN_CARD_TEMPLATE. */
	private static final String UIN_CARD_TEMPLATE = "RPR_UIN_CARD_TEMPLATE";

	/** The Constant UIN. */
	private static final String UIN = "UIN";

	/** The Constant RID. */
	private static final String RID = "RID";

	/** The Constant FACE. */
	private static final String FACE = "Face";

	/** The Constant UIN_CARD_PDF. */
	private static final String UIN_CARD_PDF = "uinPdf";

	/** The Constant UIN_TEXT_FILE. */
	private static final String UIN_TEXT_FILE = "textFile";

	/** The Constant APPLICANT_PHOTO. */
	private static final String APPLICANT_PHOTO = "ApplicantPhoto";

	/** The Constant QRCODE. */
	private static final String QRCODE = "QrCode";

	/** The reg proc logger. */
	private static Logger regProcLogger = RegProcessorLogger.getLogger(PrintServiceImpl.class);

	/** The core audit request builder. */
	@Autowired
	private AuditLogRequestBuilder auditLogRequestBuilder;

	/** The utility. */
	@Autowired
	private Utilities utility;

	/** The attributes. */
	private Map<String, Object> attributes = new LinkedHashMap<>();

	/** The packet info manager. */
	@Autowired
	private PacketInfoManager<Identity, ApplicantInfoDto> packetInfoManager;

	/** The template generator. */
	@Autowired
	private TemplateGenerator templateGenerator;

	/** The uin card generator. */
	@Autowired
	private UinCardGenerator<ByteArrayOutputStream> uinCardGenerator;

	/** The rest client service. */
	@Autowired
	private RegistrationProcessorRestClientService<Object> restClientService;

	/** The registration status service. */
	@Autowired
	RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;

	/** The qr code generator. */
	@Autowired
	private QrCodeGenerator<QrVersion> qrCodeGenerator;

	/** The is transactional. */
	private boolean isTransactionSuccessful = false;

	/** The Constant INDIVIDUAL_BIOMETRICS. */
	private static final String INDIVIDUAL_BIOMETRICS = "individualBiometrics";

	/** The cbeffutil. */
	@Autowired
	private CbeffUtil cbeffutil;

	/*
	 * 
	 * (non-Javadoc)
	 * 
	 * @see
	 * io.mosip.registration.processor.core.spi.print.service.PrintService#getPdf(
	 * java.lang.String)
	 */
	@Override
	@SuppressWarnings("rawtypes")
	public Map<String, byte[]> getDocuments(IdType idType, String idValue) {
		Map<String, byte[]> byteMap = new HashMap<>();
		String uin = null;
		String description = null;

		try {
			if (idType.toString().equalsIgnoreCase(UIN)) {
				uin = idValue;
			} else if (idType.toString().equalsIgnoreCase(RID)) {
				uin = packetInfoManager.getUINByRid(idValue).get(0);
				if (uin == null) {
					regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
							LoggerFileConstant.REGISTRATIONID.toString(), uin,
							PlatformErrorMessages.RPR_PRT_UIN_NOT_FOUND_IN_DATABASE.name());
					throw new UINNotFoundInDatabase(PlatformErrorMessages.RPR_PRT_UIN_NOT_FOUND_IN_DATABASE.getCode());
				}
			}

			IdResponseDTO response = getIdRepoResponse(uin);

			boolean isPhotoSet = setApplicantPhoto(response);
			if (!isPhotoSet) {
				regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), uin,
						PlatformErrorMessages.RPR_PRT_APPLICANT_PHOTO_NOT_SET.name());
			}
			String jsonString = new JSONObject((Map) response.getResponse().getIdentity()).toString();
			setTemplateAttributes(jsonString, attributes);
			attributes.put(UIN, uin);

			byte[] textFileByte = createTextFile();
			byteMap.put(UIN_TEXT_FILE, textFileByte);

			boolean isQRcodeSet = setQrCode(textFileByte);
			if (!isQRcodeSet) {
				regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), uin,
						PlatformErrorMessages.RPR_PRT_QRCODE_NOT_SET.name());
			}

			// getting template and placing original values
			InputStream uinArtifact = templateGenerator.getTemplate(UIN_CARD_TEMPLATE, attributes, primaryLang);
			if (uinArtifact == null) {
				regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), idValue,
						PlatformErrorMessages.RPR_TEM_PROCESSING_FAILURE.name());
				throw new TemplateProcessingFailureException(
						PlatformErrorMessages.RPR_TEM_PROCESSING_FAILURE.getCode());
			}

			// generating pdf
			ByteArrayOutputStream pdf = uinCardGenerator.generateUinCard(uinArtifact, UinCardType.PDF);

			byte[] pdfbytes = pdf.toByteArray();
			byteMap.put(UIN_CARD_PDF, pdfbytes);

			byte[] uinbyte = attributes.get(UIN).toString().getBytes();
			byteMap.put(UIN, uinbyte);

			isTransactionSuccessful = true;

		} catch (QrcodeGenerationException e) {
			description = "Error while QR Code Generation";
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					idValue, PlatformErrorMessages.RPR_PRT_QRCODE_NOT_GENERATED.name() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			throw new PDFGeneratorException(PDFGeneratorExceptionCodeConstant.PDF_EXCEPTION.getErrorCode(),
					e.getMessage() + ExceptionUtils.getStackTrace(e));

		} catch (UINNotFoundInDatabase e) {
			description = "UIN not found in database for id" + idValue;
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					idValue, PlatformErrorMessages.RPR_PRT_UIN_NOT_FOUND_IN_DATABASE.name() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			throw new PDFGeneratorException(PDFGeneratorExceptionCodeConstant.PDF_EXCEPTION.getErrorCode(),
					e.getMessage() + ExceptionUtils.getStackTrace(e));

		} catch (TemplateProcessingFailureException e) {
			description = "Error while Template Processing";
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					idValue, PlatformErrorMessages.RPR_TEM_PROCESSING_FAILURE.name() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			throw new TemplateProcessingFailureException(PlatformErrorMessages.RPR_TEM_PROCESSING_FAILURE.getCode());

		} catch (PDFGeneratorException e) {
			description = "Error while pdf generation";
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					idValue, PlatformErrorMessages.RPR_PRT_PDF_NOT_GENERATED.name() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			throw new PDFGeneratorException(PDFGeneratorExceptionCodeConstant.PDF_EXCEPTION.getErrorCode(),
					e.getMessage() + ExceptionUtils.getStackTrace(e));

		} catch (ApisResourceAccessException | IOException | ParseException
				| io.mosip.kernel.core.exception.IOException e) {
			description = "Internal error occured while processing packet id" + idValue;
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					idValue, PlatformErrorMessages.RPR_PRT_PDF_GENERATION_FAILED.name() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			throw new PDFGeneratorException(PDFGeneratorExceptionCodeConstant.PDF_EXCEPTION.getErrorCode(),
					e.getMessage() + ExceptionUtils.getStackTrace(e));

		} catch (Exception ex) {
			description = "Process stopped due to some internal error";
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					idValue, description + ex.getMessage() + ExceptionUtils.getStackTrace(ex));
			throw new PDFGeneratorException(PDFGeneratorExceptionCodeConstant.PDF_EXCEPTION.getErrorCode(),
					ex.getMessage() + ExceptionUtils.getStackTrace(ex));

		} finally {
			String eventId = "";
			String eventName = "";
			String eventType = "";
			if (isTransactionSuccessful) {
				description = "Pdf generated and sent to print stage";
				eventId = EventId.RPR_402.toString();
				eventName = EventName.UPDATE.toString();
				eventType = EventType.BUSINESS.toString();
			} else {

				description = "Pdf was not generated for uin card template";
				eventId = EventId.RPR_405.toString();
				eventName = EventName.EXCEPTION.toString();
				eventType = EventType.SYSTEM.toString();
			}
			auditLogRequestBuilder.createAuditRequestBuilder(description, eventId, eventName, eventType, uin,
					ApiName.AUDIT);
		}

		return byteMap;
	}

	/**
	 * Gets the id repo response.
	 *
	 * @param uin
	 *            the uin
	 * @return the id repo response
	 * @throws ApisResourceAccessException
	 *             the apis resource access exception
	 */
	private IdResponseDTO getIdRepoResponse(String uin) throws ApisResourceAccessException {
		List<String> pathsegments = new ArrayList<>();
		pathsegments.add(uin);

		String queryParamName = "type";
		String queryParamValue = "all";

		IdResponseDTO response = (IdResponseDTO) restClientService.getApi(ApiName.IDREPOSITORY, pathsegments,
				queryParamName, queryParamValue, IdResponseDTO.class);

		if (response == null || response.getResponse() == null) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					uin, PlatformErrorMessages.RPR_PRT_IDREPO_RESPONSE_NULL.name());
			throw new IDRepoResponseNull(PlatformErrorMessages.RPR_PRT_IDREPO_RESPONSE_NULL.getCode());
		}

		return response;
	}

	/**
	 * Creates the text file.
	 *
	 * @return the byte[]
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	private byte[] createTextFile() throws IOException {
		JsonFileDTO jsonDto = new JsonFileDTO();
		jsonDto.setId("mosip.registration.print.send");
		jsonDto.setVersion("1.0");
		jsonDto.setRequestTime(DateUtils.getUTCCurrentDateTimeString("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));

		JsonRequestDTO request = new JsonRequestDTO();
		request.setNameLang1((String) attributes.get(UINCardConstant.NAME + "_" + primaryLang));
		request.setAddressLine1Lang1((String) attributes.get(UINCardConstant.ADDRESSLINE1 + "_" + primaryLang));
		request.setAddressLine2Lang1((String) attributes.get(UINCardConstant.ADDRESSLINE2 + "_" + primaryLang));
		request.setAddressLine3Lang1((String) attributes.get(UINCardConstant.ADDRESSLINE3 + "_" + primaryLang));
		request.setRegionLang1((String) attributes.get(UINCardConstant.REGION + "_" + primaryLang));
		request.setProvinceLang1((String) attributes.get(UINCardConstant.PROVINCE + "_" + primaryLang));
		request.setCityLang1((String) attributes.get(UINCardConstant.CITY + "_" + primaryLang));
		request.setNameLang2((String) attributes.get(UINCardConstant.NAME + "_" + secondaryLang));
		request.setAddressLine1Lang2((String) attributes.get(UINCardConstant.ADDRESSLINE1 + "_" + secondaryLang));
		request.setAddressLine2Lang2((String) attributes.get(UINCardConstant.ADDRESSLINE2 + "_" + secondaryLang));
		request.setAddressLine3Lang2((String) attributes.get(UINCardConstant.ADDRESSLINE3 + "_" + secondaryLang));
		request.setRegionLang2((String) attributes.get(UINCardConstant.REGION + "_" + secondaryLang));
		request.setProvinceLang2((String) attributes.get(UINCardConstant.PROVINCE + "_" + secondaryLang));
		request.setCityLang2((String) attributes.get(UINCardConstant.CITY + "_" + secondaryLang));
		request.setPostalCode((String) attributes.get(UINCardConstant.POSTALCODE));
		request.setPhoneNumber((String) attributes.get(UINCardConstant.PHONE));

		jsonDto.setRequest(request);

		File jsonText = new File(attributes.get(UIN).toString() + TXT);
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
		mapper.writeValue(jsonText, jsonDto);

		InputStream fileStream = new FileInputStream(jsonText);
		byte[] jsonTextFileBytes = IOUtils.toByteArray(fileStream);
		fileStream.close();
		FileUtils.forceDelete(jsonText);

		return jsonTextFileBytes;
	}

	/**
	 * Sets the qr code.
	 *
	 * @param textFileByte
	 *            the text file byte
	 * @return true, if successful
	 * @throws QrcodeGenerationException
	 *             the qrcode generation exception
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	private boolean setQrCode(byte[] textFileByte) throws QrcodeGenerationException, IOException {
		String qrString = new String(textFileByte);
		boolean isQRCodeSet = false;
		byte[] qrCodeBytes = qrCodeGenerator.generateQrCode(qrString, QrVersion.V30);
		if (qrCodeBytes != null) {
			String imageString = CryptoUtil.encodeBase64String(qrCodeBytes);
			attributes.put(QRCODE, "data:image/png;base64," + imageString);
			isQRCodeSet = true;
		}

		return isQRCodeSet;
	}

	/**
	 * Sets the applicant photo.
	 *
	 * @param response
	 *            the response
	 * @return true, if successful
	 * @throws Exception
	 *             the exception
	 */
	private boolean setApplicantPhoto(IdResponseDTO response) throws Exception {
		String value = null;
		boolean isPhotoSet = false;

		if (response == null || response.getResponse() == null) {
			return Boolean.FALSE;
		}
		if (response.getResponse().getDocuments() != null) {
			List<Documents> documents = response.getResponse().getDocuments();
			for (Documents doc : documents) {
				if (doc.getCategory().equals(INDIVIDUAL_BIOMETRICS)) {
					value = doc.getValue();
					break;
				}
			}
		}
		if (value != null) {
			CbeffToBiometricUtil util = new CbeffToBiometricUtil(cbeffutil);
			List<String> subtype = new ArrayList<>();
			byte[] photobyte = util.getPhoto(value, FACE, subtype);
			String imageString = CryptoUtil.encodeBase64String(photobyte);
			attributes.put(APPLICANT_PHOTO, "data:image/png;base64," + imageString);
			isPhotoSet = true;
		}

		return isPhotoSet;
	}

	/**
	 * Gets the artifacts.
	 *
	 * @param idJsonString
	 *            the id json string
	 * @param attribute
	 *            the attribute
	 * @return the artifacts
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	@SuppressWarnings("unchecked")
	private void setTemplateAttributes(String idJsonString, Map<String, Object> attribute) throws IOException {
		try {
			JSONObject demographicIdentity = JsonUtil.objectMapperReadValue(idJsonString, JSONObject.class);
			if (demographicIdentity == null)
				throw new IdentityNotFoundException(PlatformErrorMessages.RPR_PIS_IDENTITY_NOT_FOUND.getMessage());

			String mapperJsonString = Utilities.getJson(utility.getConfigServerFileStorageURL(),
					utility.getGetRegProcessorIdentityJson());
			JSONObject mapperJson = JsonUtil.objectMapperReadValue(mapperJsonString, JSONObject.class);
			JSONObject mapperIdentity = JsonUtil.getJSONObject(mapperJson,
					utility.getGetRegProcessorDemographicIdentity());

			List<String> mapperJsonKeys = new ArrayList<>(mapperIdentity.keySet());
			for (String key : mapperJsonKeys) {
				JSONObject jsonValue = JsonUtil.getJSONObject(mapperIdentity, key);
				Object object = JsonUtil.getJSONValue(demographicIdentity, (String) jsonValue.get(VALUE));
				if (object instanceof ArrayList) {
					JSONArray node = JsonUtil.getJSONArray(demographicIdentity, (String) jsonValue.get(VALUE));
					JsonValue[] jsonValues = mapJsonNodeToJavaObject(JsonValue.class, node);
					for (int count = 0; count < jsonValues.length; count++) {
						String lang = jsonValues[count].getLanguage();
						attribute.put(key + "_" + lang, jsonValues[count].getValue());
					}
				} else if (object instanceof LinkedHashMap) {
					JSONObject json = JsonUtil.getJSONObject(demographicIdentity, (String) jsonValue.get(VALUE));
					attribute.put(key, json.get(VALUE));
				} else {
					attribute.put(key, object);
				}
			}

		} catch (JsonParseException | JsonMappingException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					null, "Error while parsing Json file" + ExceptionUtils.getStackTrace(e));
			throw new ParsingException(PlatformErrorMessages.RPR_SYS_JSON_PARSING_EXCEPTION.getMessage(), e);
		}
	}

	/**
	 * Map json node to java object.
	 *
	 * @param <T>
	 *            the generic type
	 * @param genericType
	 *            the generic type
	 * @param demographicJsonNode
	 *            the demographic json node
	 * @return the t[]
	 */
	@SuppressWarnings("unchecked")
	private <T> T[] mapJsonNodeToJavaObject(Class<? extends Object> genericType, JSONArray demographicJsonNode) {
		String language;
		String value;
		T[] javaObject = (T[]) Array.newInstance(genericType, demographicJsonNode.size());
		try {
			for (int i = 0; i < demographicJsonNode.size(); i++) {

				T jsonNodeElement = (T) genericType.newInstance();

				JSONObject objects = JsonUtil.getJSONObjectFromArray(demographicJsonNode, i);
				language = (String) objects.get(LANGUAGE);
				value = (String) objects.get(VALUE);

				Field languageField = jsonNodeElement.getClass().getDeclaredField(LANGUAGE);
				languageField.setAccessible(true);
				languageField.set(jsonNodeElement, language);

				Field valueField = jsonNodeElement.getClass().getDeclaredField(VALUE);
				valueField.setAccessible(true);
				valueField.set(jsonNodeElement, value);

				javaObject[i] = jsonNodeElement;
			}
		} catch (InstantiationException | IllegalAccessException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					null, "Error while Creating Instance of generic type" + ExceptionUtils.getStackTrace(e));
			throw new InstantanceCreationException(PlatformErrorMessages.RPR_SYS_INSTANTIATION_EXCEPTION.getMessage(),
					e);

		} catch (NoSuchFieldException | SecurityException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					null, "no such field exception" + ExceptionUtils.getStackTrace(e));
			throw new FieldNotFoundException(PlatformErrorMessages.RPR_SYS_NO_SUCH_FIELD_EXCEPTION.getMessage(), e);

		}

		return javaObject;
	}

}
