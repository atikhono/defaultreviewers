package ut.com.parallels.bitbucket.plugins;

import org.junit.Test;
import com.parallels.bitbucket.plugins.api.MyPluginComponent;
import com.parallels.bitbucket.plugins.impl.MyPluginComponentImpl;

import static org.junit.Assert.assertEquals;

public class MyComponentUnitTest
{
    @Test
    public void testMyName()
    {
        MyPluginComponent component = new MyPluginComponentImpl(null);
        assertEquals("names do not match!", "myComponent",component.getName());
    }
}