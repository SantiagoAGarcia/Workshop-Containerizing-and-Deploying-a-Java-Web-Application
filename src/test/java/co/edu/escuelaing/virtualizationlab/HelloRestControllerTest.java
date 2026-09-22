package co.edu.escuelaing.virtualizationlab;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HelloRestControllerTest {

    @Test
    void returnsGreetingWithGivenName() {
        HelloRestController controller = new HelloRestController();
        assertThat(controller.greeting("Pedro")).isEqualTo("Hello, Pedro!");
    }

    @Test
    void returnsDefaultGreetingWhenNameIsMissing() {
        HelloRestController controller = new HelloRestController();
        assertThat(controller.greeting("World")).isEqualTo("Hello, World!");
    }
}
