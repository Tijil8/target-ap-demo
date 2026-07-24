package life.tijil.ap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ApApplication

fun main(args: Array<String>) {
    runApplication<ApApplication>(*args)
}
