import sdg.IStepExecutor;
import sdg.ioc.ContextRegistry;
import sdg.ioc.IContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import static org.mockito.Mockito.*;
import org.mockito.Mock;
import com.lesfurets.jenkins.unit.BasePipelineTest

import sdg.stages.SimplyPrint

/**
 * Example test class
 */
public class SimplyPrintTestFunctional extends BasePipelineTest{

    private gauntlet;

    @Before
    public void setup() {

        setUp()
        // def getGauntlet = loadScript('vars/getGauntlet.groovy')
        def getGauntEnv = loadScript('vars/getGauntEnv.groovy')

        // mock gauntlet
        IStepExecutor steps =  mock(IStepExecutor.class);

        // mock steps
        IContext context = mock(IContext.class);
        when(context.getStepExecutor()).thenReturn(steps);
        when(context.isDefault()).thenReturn(false);
        when(context.getStepExecutor().getGauntEnv(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()
        )).thenReturn(getGauntEnv.call("NA","NA","NA","NA","NA"));
        ContextRegistry.registerContext(context);

        // define mocked gauntlet
        gauntlet = new sdg.Gauntlet()
        gauntlet.construct("NA","NA","NA","NA","NA")

        // Mock the stage method
        doAnswer({ invocation -> 
            Runnable body = invocation.getArgument(1);
            body.run();
            return null;
        }).when(gauntlet.stepExecutor).stage(anyString(), any(Runnable.class));
    }

    @Test
    public void TestSimplyPrint(){
        def sp = new SimplyPrint()
        def board = "pluto"
        def cls = sp.getCls()

        Assert.assertEquals(sp.getStageName(), "SimplyPrint")
        Assert.assertEquals(gauntlet.get_env("debug_level"),1)
        cls.call(gauntlet, board)
        Assert.assertEquals(gauntlet.get_env("debug_level"),2)
    }
}
