import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.rpc.Protocol;
import org.junit.Test;

/**
 *
 * @since 2022/11/25
 * @author dingrui
 */
public class ProtocolTest {

    @Test
    public void test00() {
        Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
        System.out.println(protocol);
    }
}
