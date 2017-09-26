package info.jonsnow.jonsnows3fileuploaderservice;


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.aioobe.cloudconvert.CloudConvertService;
import org.aioobe.cloudconvert.ConvertProcess;
import org.aioobe.cloudconvert.ProcessStatus;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.rest.api.v2010.account.CallCreator;
import com.twilio.sdk.Twilio;
import com.twilio.twiml.Play;
import com.twilio.twiml.VoiceResponse;
import com.twilio.type.PhoneNumber;
/**
 * Created by vthiagarajan on 9/11/17.
 */
@CrossOrigin(origins={"*"}, maxAge=3600L)
@RestController
public class JonSnowS3FileUploaderController {

    private final Logger logger = LoggerFactory.getLogger(JonSnowS3FileUploaderController.class);

    //Save the uploaded file to this folder
    private static String UPLOADED_FOLDER = "/jonsnow/fileuploader/";
    // Find your Account Sid and Token at twilio.com/user/account
    public static final String ACCOUNT_SID = "ACe9bd589acf25766773e0eec2f1f99cfb";
    public static final String AUTH_TOKEN = "ef3a04b85d073b19c6dff0cf478a5d63";


    /***
     * Receives the recorded file url and produces Twilio based response.
     */
    @RequestMapping("/playmessage")
    public void greeting(HttpServletResponse response, @RequestParam(value="recordFilePath") String recordFilePath){

    // Create a TwiML response and add our friendly message.
        VoiceResponse twiml = new VoiceResponse.Builder()
            .play(new Play.Builder(recordFilePath).build())
            .build();

        response.setContentType("application/xml");
        try {
            response.getWriter().print(twiml.toXml());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Upload a file that will be in ogg format initially and then
     * convert into mp3 and load that into S3 and get back a url.
     */
    @RequestMapping("/upload")
    public Map<String, String> upload( @RequestParam("file") MultipartFile uploadfile ) {

        HashMap<String, String> map = new HashMap<>();
        logger.debug("Single file upload!");

        String uploadedUrl = "";

        try {

            uploadedUrl = saveUploadedFiles(uploadfile);

        } catch (IOException e) {

        }

        map.put("s3Location",uploadedUrl);
        return map;

    }

    /**
     * Upload a file that will be in Mp3 format and stream into S3 directly.
     */
    @RequestMapping("/uploadstream")
    public Map<String, String> uploadStream( @RequestParam("file") MultipartFile uploadfile ) {

        HashMap<String, String> map = new HashMap<>();
        logger.debug("Single file upload!");

        String uploadedUrl = "";

        try {

            uploadedUrl = streamToS3(uploadfile);

        } catch (IOException e) {

        }

        map.put("s3Location",uploadedUrl);
        return map;

    }

    /***
     * Template method to make the actual call.
     */
    @RequestMapping("/makeTheCall")
    private Map<String, String> makeTheCall(
        @RequestParam("phoneNumber") String phoneNumber,
        @RequestParam("recordedFileUrl") String recordedFileUrl,
        @RequestParam("yyyymmddhhmmString") String yyyymmddhhmmString
        ) {

        logger.info("Recorder File Url" + recordedFileUrl);
        logger.info("phoneNumber" + phoneNumber);
        HashMap<String, String> map = new HashMap<>();

        callThem(phoneNumber,recordedFileUrl);

        map.put("message","Your Mokri is scheduled.");
        map.put("input",phoneNumber+" "+recordedFileUrl+" "+yyyymmddhhmmString);

        return map;

    }

    /**
     * Actual method in which files are handled.
     * */
    private String saveUploadedFiles(MultipartFile file) throws IOException {

        FileUtils.cleanDirectory(new File(UPLOADED_FOLDER));

            byte[] bytes = file.getBytes();
            Path path = Paths.get(UPLOADED_FOLDER + file.getOriginalFilename());
            Files.write(path, bytes);

            File initialFile = new File(path.toString());

            File mp3FormatFile = new File(UPLOADED_FOLDER+file.getOriginalFilename().replace("ogg","mp3"));

            ////////////
            try {

                // Create service object
                CloudConvertService service = new
                    CloudConvertService("Gb1Wz3RREV7eZKf-ts6xiZtgVHJtlWoXDLDbR_8R1ujr7G5qc_LGIfunAaJkrAmlndJbs8YCCC3f2OALTJL8Lw");

                // Create conversion process
                ConvertProcess process = service.startProcess("ogg", "mp3");

                // Perform conversion
                process.startConversion(initialFile);

                // Wait for result
                ProcessStatus status;
                waitLoop: while (true) {
                    status = process.getStatus();

                    switch (status.step) {
                        case FINISHED: break waitLoop;
                        case ERROR: throw new RuntimeException(status.message);
                    }

                    // Be gentle
                    Thread.sleep(200);
                }

                // Download result
                service.download(status.output.url, mp3FormatFile);

                // Clean up
                process.delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
            ///////////

            AWSCredentials credentials = new BasicAWSCredentials("AKIAIGW5G7L4YACSLULQ", "sN8vLsCNAm8djXHoXUhxSe+KFLbApujL7ekma48O");
        /*Creating a client object using the credentials*/
            AmazonS3 s3client = new AmazonS3Client(credentials);

            try {
                System.out.println("Uploading a new object to S3 from a file\n");
                //File files = new File(file);

                String objectName = System.currentTimeMillis()+mp3FormatFile.getName();

                s3client.putObject(new PutObjectRequest(
                    "jonsnow-vinodh", objectName, mp3FormatFile).withCannedAcl(CannedAccessControlList.PublicRead));

                String s3DirectUrl = "https://s3.us-east-2.amazonaws.com/jonsnow-vinodh/"+objectName;
                FileUtils.cleanDirectory(new File(UPLOADED_FOLDER));

                return s3DirectUrl;

            } catch (AmazonServiceException ase) {
                System.out.println("Caught an AmazonServiceException, which " +
                    "means your request made it " +
                    "to Amazon S3, but was rejected with an error response" +
                    " for some reason.");
                System.out.println("Error Message:    " + ase.getMessage());
                System.out.println("HTTP Status Code: " + ase.getStatusCode());
                System.out.println("AWS Error Code:   " + ase.getErrorCode());
                System.out.println("Error Type:       " + ase.getErrorType());
                System.out.println("Request ID:       " + ase.getRequestId());
            } catch (AmazonClientException ace) {
                System.out.println("Caught an AmazonClientException, which " +
                    "means the client encountered " +
                    "an internal error while trying to " +
                    "communicate with S3, " +
                    "such as not being able to access the network.");
                System.out.println("Error Message: " + ace.getMessage());
            }

        FileUtils.cleanDirectory(new File(UPLOADED_FOLDER));
        return null;
    }

    /**
     * Stream the actual file to S3.
     * */
    //save file
    private String streamToS3(MultipartFile file) throws IOException {
        System.out.println("Working Directory = " +
            System.getProperty("user.dir"));

            byte[] bytes = file.getBytes();

            AWSCredentials credentials = new BasicAWSCredentials("AKIAIGW5G7L4YACSLULQ", "sN8vLsCNAm8djXHoXUhxSe+KFLbApujL7ekma48O");
        /*Creating a client object using the credentials*/
            AmazonS3 s3client = new AmazonS3Client(credentials);

            try {
                System.out.println("Uploading a new object to S3 from a file\n");

                String objectName = System.currentTimeMillis()+file.getOriginalFilename();
                InputStream stream = new ByteArrayInputStream(bytes);
                ObjectMetadata meta = new ObjectMetadata();
                meta.setContentLength(bytes.length);
                meta.setContentType("audio/mpeg");
                s3client.putObject(new PutObjectRequest(
                    "jonsnow-vinodh", objectName, stream,meta).withCannedAcl(CannedAccessControlList.PublicRead));

                String s3DirectUrl = "https://s3.us-east-2.amazonaws.com/jonsnow-vinodh/"+objectName;

                return s3DirectUrl;

            } catch (AmazonServiceException ase) {
                System.out.println("Caught an AmazonServiceException, which " +
                    "means your request made it " +
                    "to Amazon S3, but was rejected with an error response" +
                    " for some reason.");
                System.out.println("Error Message:    " + ase.getMessage());
                System.out.println("HTTP Status Code: " + ase.getStatusCode());
                System.out.println("AWS Error Code:   " + ase.getErrorCode());
                System.out.println("Error Type:       " + ase.getErrorType());
                System.out.println("Request ID:       " + ase.getRequestId());
            } catch (AmazonClientException ace) {
                System.out.println("Caught an AmazonClientException, which " +
                    "means the client encountered " +
                    "an internal error while trying to " +
                    "communicate with S3, " +
                    "such as not being able to access the network.");
                System.out.println("Error Message: " + ace.getMessage());
            }

        return null;
    }

    public void callThem(String phoneNumber, String recordedUrl) {
        com.twilio.Twilio.init("ACe9bd589acf25766773e0eec2f1f99cfb", "ef3a04b85d073b19c6dff0cf478a5d63");

        try {

            CallCreator creator =
                Call.creator(
                    ACCOUNT_SID,
                    new PhoneNumber(phoneNumber),
                    new PhoneNumber("(682) 200-8898"),
                    new URI("http://vinodh.adaptainer.io/services/playmessage?recordFilePath="+recordedUrl)
                ).setMachineDetection("DetectMessageEnd")
                    .setMachineDetectionTimeout(5);

            // Make the call in 2 sec
            new java.util.Timer().schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        Call call = creator.create();
                        System.out.println(call.getSid());
                        System.out.println(call.getStatus().toString());
                    }
                },
                2000
            );
        } catch (URISyntaxException e) {
            System.err.println("womp womp");
            System.exit(1);
        }
    }

}
