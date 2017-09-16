package info.jonsnow.jonsnows3fileuploaderservice;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    public String upload( @RequestParam("file") MultipartFile uploadfile ) {


        logger.debug("Single file upload!");

        String uploadedUrl = "";

        try {

            uploadedUrl = saveUploadedFiles(Arrays.asList(uploadfile));

        } catch (IOException e) {

        }

        return uploadedUrl;

    }

    //save file
    private String saveUploadedFiles(List<MultipartFile> files) throws IOException {
        System.out.println("Working Directory = " +
            System.getProperty("user.dir"));

        for (MultipartFile file : files) {

            if (file.isEmpty()) {
                continue; //next pls
            }

            byte[] bytes = file.getBytes();
            Path path = Paths.get(UPLOADED_FOLDER + file.getOriginalFilename());
            Files.write(path, bytes);

            File initialFile = new File(path.toString());

            AWSCredentials credentials = new BasicAWSCredentials("AKIAIGW5G7L4YACSLULQ", "sN8vLsCNAm8djXHoXUhxSe+KFLbApujL7ekma48O");
        /*Creating a client object using the credentials*/
            AmazonS3 s3client = new AmazonS3Client(credentials);

            try {
                System.out.println("Uploading a new object to S3 from a file\n");
                //File files = new File(file);

                String objectName = System.currentTimeMillis()+file.getOriginalFilename();

                s3client.putObject(new PutObjectRequest(
                    "jonsnow-vinodh", objectName, initialFile).withCannedAcl(CannedAccessControlList.PublicRead));

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
