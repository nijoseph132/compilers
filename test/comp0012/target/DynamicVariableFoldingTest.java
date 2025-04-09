package comp0012.target;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.assertEquals;

/**
 * Test dynamic variable folding
 */

public class DynamicVariableFoldingTest
{
    DynamicVariableFolding dvf = new DynamicVariableFolding();
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();

    @Before
    public void setUpStreams()
    {
        System.setOut(new PrintStream(outContent));
    }

    @After
    public void cleanUpStreams()
    {
        System.setOut(null);
    }

    @Test
    public void testMethodOne()
    {
        assertEquals(1301, dvf.methodOne());
    }

    @Test
    public void testMethodTwoOut()
    {
        dvf.methodTwo();
        assertEquals("true\n", outContent.toString());
    }

    @Test
    public void testMethodTwoReturn()
    {
        assertEquals(true, dvf.methodTwo());
    }

    @Test
    public void testMethodThree()
    {
        assertEquals(84, dvf.methodThree());
    }
    
    @Test
    public void testMethodFour(){
        assertEquals(24, dvf.methodFour());
    }

    @Test
    public void testIfElseBlock() {
        assertEquals(9, dvf.ifElseBlock());
    }

    @Test
    public void testNegativeIfElseBlock() {
        assertEquals(1, dvf.negativeIfElseBlock());
    }

    @Test
    public void testIfBlock() {
        assertEquals(3, dvf.ifBlock());
    }

    @Test
    public void testNegativeIfBlock() {
        assertEquals(0, dvf.negativeIfBlock());
    }

    @Test
    public void testNestedIfBlock() {
        assertEquals(2, dvf.nestedIfBlock());
    }
    
    @Test
    public void testElseIfBlock() {
        assertEquals(23, dvf.elseIfBlock());
    }

    @Test
    public void testDeadLocalVariables() {
        assertEquals(38, dvf.deadLocalVariables());
    }

    @Test
    public void testDeadLocalVariables2() {
        assertEquals(-12, dvf.deadLocalVariables2());
    }

    @Test
    public void testDeadLocalVariables3() {
        assertEquals(24, dvf.deadLocalVariables3());
    }

    @Test
    public void testDeadLocalVariables4() {
        assertEquals(5, dvf.deadLocalVariables4());
    }

    @Test 
    public void testLoops() {
        assertEquals(13, dvf.loops());
    }

    @Test 
    public void testLoops2() {
        assertEquals(16, dvf.loops2());
    }

    @Test 
    public void testLoops3() {
        assertEquals(3, dvf.loops3());
    }

    @Test 
    public void testLoops4() {
        assertEquals(6, dvf.loops4());
    }

}