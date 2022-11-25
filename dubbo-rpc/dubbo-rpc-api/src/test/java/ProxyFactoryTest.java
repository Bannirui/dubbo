import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.rpc.ProxyFactory;
import org.junit.Test;

/**
 *
 * @since 2022/11/25
 * @author dingrui
 */
public class ProxyFactoryTest {

    @Test
    public void test00(){
        ProxyFactory ans = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
        System.out.println();
    }
}
