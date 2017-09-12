package info.jonsnow.jonsnows3fileuploaderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;

@SpringBootApplication
public class JonsnowS3FileUploaderServiceApplication extends SpringBootServletInitializer {

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(JonsnowS3FileUploaderServiceApplication.class);
	}

	public static void main(String[] args) {
		SpringApplication.run(JonsnowS3FileUploaderServiceApplication.class, args);
	}
}
