import com.lesfurets.jenkins.unit.PipelineTestHelper
import com.lesfurets.jenkins.unit.BasePipelineTest
import spock.lang.Specification

class SpockTest extends Specification {

    def "check case-insensitive equality of 2 strings"() {
        given:
            String str1 = "hello"
            String str2 = "HELLO"  
        when:
            str1 = str1.toLowerCase()
            str2 = str2.toLowerCase()    
        then:
            str1 == str2
    }

    def "check addition of 2 numbers"() {     
        given:
            int input1 = 10
            int input2 = 25
        when:
            int result = input1 + input2
        then:
            result == 35
    }
}