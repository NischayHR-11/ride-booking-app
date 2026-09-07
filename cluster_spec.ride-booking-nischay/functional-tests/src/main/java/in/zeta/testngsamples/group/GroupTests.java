package in.zeta.testngsamples.group;

import org.testng.Assert;
import org.testng.annotations.Test;
import io.restassured.RestAssured;
import io.restassured.response.Response;


public class GroupTests {
    @Test(groups = {"p0", "unit"})
    void testGoogleGet_Success(){
        Response response = RestAssured.get("https://www.google.com");
        Assert.assertEquals(response.getStatusCode(), 200);
    }
}