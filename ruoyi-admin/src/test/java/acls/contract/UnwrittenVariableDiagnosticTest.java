package acls.contract;

import com.ruoyi.acls.contract.UnwrittenVariableDiagnostic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = org.ruoyi.RuoYiAIApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class UnwrittenVariableDiagnosticTest {

    @Autowired
    private UnwrittenVariableDiagnostic diagnostic;

    @Test
    void diagnose() {
        var result = diagnostic.diagnose("stable_narrow");
        System.out.println(result.toReport());
    }
}
