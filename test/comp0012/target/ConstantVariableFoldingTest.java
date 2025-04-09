package comp0012.target;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test constant variable folding
 */
public class ConstantVariableFoldingTest {

    ConstantVariableFolding cvf = new ConstantVariableFolding();

    @Test
    public void testMethodOne(){
        assertEquals(3650, cvf.methodOne());
    }

    @Test
    public void testMethodTwo(){
        assertEquals(1.67, cvf.methodTwo(), 0.001);
    }

    @Test
    public void testMethodThree(){
        assertEquals(false, cvf.methodThree());
    }
    
    @Test
    public void testMethodFour(){
        assertEquals(true, cvf.methodFour());
    }
    
    @Test
    public void testMethodFive() {
        assertEquals(1, cvf.methodFive());
    }

    @Test
    public void testMethodSix() {
        assertEquals(1431656098, cvf.methodSix());
    }

    @Test
    public void testMethodSeven() {
        assertEquals(1.9500002F, cvf.methodSeven(), 0.0001);

    }

    @Test
    public void testMethodEight() {
        assertEquals(3.0001, cvf.methodEight(), 0.0001);
    }

    @Test
    public void testMethodNine() {
        assertEquals(251.6665, cvf.methodNine(), 0.0001);
    }

    @Test
    public void testMethodTen() {
        assertEquals(3.482050807568877, cvf.methodTen(), 0.0001);
    }

    @Test
    public void testMethodEleven() {
        assertEquals(50, cvf.methodEleven());
    }
}
