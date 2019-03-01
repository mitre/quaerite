package test.java.org.mitre.quaerite;

import org.junit.jupiter.api.Test;
import org.mitre.quaerite.stats.YatesChi;

public class YatesChiTest {

    @Test
    public void testBasic() {
        YatesChi chi = new YatesChi();
        double fC = 1;
        double fT = 1138;
        double bC = 91;
        double bT = 124456;
        double a = 0;
        double b = fT-fC;
        double c = bC;
        double d = bT-bC;
        System.out.println(chi.calculateValue(a, b, c, d));
        //32 .15
        //91 .12
    }
}
