import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Instant;

public class TestMemcachedServer {
    final static org.slf4j.Logger logger = LoggerFactory.getLogger(MemcachedServer.class);

    @Test
    public void testServerStartsFine() {
        MemcachedServer server = new MemcachedServer();
        try {
            server.initAndStart(new MockBaritoneConnector());
        } catch (Exception e) {
            logger.error("Exception", e);
            Assert.assertTrue(false);
        }
        Assert.assertTrue(server.checkMemcacheServer());
    }

    @Test
    public void testServerStaysRunningForTenSeconds() {
        MemcachedServer server = new MemcachedServer();
        boolean keepGoing = true;
        Instant endTime = Instant.now().plusSeconds(10);
        try {
            server.initAndStart(new MockBaritoneConnector());
            while(keepGoing) {
                logger.info("Test thread waiting on server...");
                Assert.assertTrue(server.checkMemcacheServer());
                server.waitWithoutNotify();
                if (Instant.now().isAfter(endTime)) {
                    keepGoing = false;
                }
            }
        } catch (Exception e) {
            logger.error("Exception", e);
            Assert.assertTrue(false);
        }
        Assert.assertTrue(server.checkMemcacheServer());
    }

}
