package info.jonsnow.jonsnows3fileuploaderservice;


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
import com.twilio.twiml.Play;
import com.twilio.twiml.VoiceResponse;
/**
 * Created by vthiagarajan on 9/11/17.
 */
@CrossOrigin(origins={"*"}, maxAge=3600L)
@RestController
public class JonSnowS3FileUploaderController {

    private final Logger logger = LoggerFactory.getLogger(JonSnowS3FileUploaderController.class);

    //Save the uploaded file to this folder
    private static String UPLOADED_FOLDER = "/jonsnow/fileuploader/";

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

    @RequestMapping("/upload")
    public Map<String, String> upload( @RequestParam("file") MultipartFile uploadfile ) {

        HashMap<String, String> map = new HashMap<>();
        logger.debug("Single file upload!");

        String uploadedUrl = "";

        try {

            uploadedUrl = saveUploadedFiles(Arrays.asList(uploadfile));

        } catch (IOException e) {

        }

        map.put("s3Location",uploadedUrl);
        return map;

    }

    @RequestMapping("/uploadstream")
    public Map<String, String> uploadStream( @RequestParam("file") MultipartFile uploadfile ) {

        HashMap<String, String> map = new HashMap<>();
        logger.debug("Single file upload!");

        String uploadedUrl = "";

        try {

            uploadedUrl = streamToS3(Arrays.asList(uploadfile));

        } catch (IOException e) {

        }

        map.put("s3Location",uploadedUrl);
        return map;

    }

    @RequestMapping("/makeTheCall")
    private String makeTheCall() {



        return null;

    }

    //save file
    private String saveUploadedFiles(List<MultipartFile> files) throws IOException {

        FileUtils.cleanDirectory(new File(UPLOADED_FOLDER));

        for (MultipartFile file : files) {

            byte[] bytes = file.getBytes();
            Path path = Paths.get(UPLOADED_FOLDER + file.getOriginalFilename());
            Files.write(path, bytes);

            File initialFile = new File(path.toString());

            File mp3FormatFile = new File(UPLOADED_FOLDER+file.getOriginalFilename().replace("ogg","mp3"));


            ////////////
            try {

                // Create service object
                CloudConvertService service = new CloudConvertService("Gb1Wz3RREV7eZKf-ts6xiZtgVHJtlWoXDLDbR_8R1ujr7G5qc_LGIfunAaJkrAmlndJbs8YCCC3f2OALTJL8Lw");

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
                //File mp3FormatFile = new File("/Users/vthiagarajan/Downloads/DONT_DELETE_REPOS/jonsnow-s3-file-uploader-service/src/main/aal.mp3");
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
        }
        FileUtils.cleanDirectory(new File(UPLOADED_FOLDER));
        return null;
    }

    //save file
    private String streamToS3(List<MultipartFile> files) throws IOException {
        System.out.println("Working Directory = " +
            System.getProperty("user.dir"));

        for (MultipartFile file : files) {

            if (file.isEmpty()) {
                continue; //next pls
            }

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
        }
        return null;
    }

}
