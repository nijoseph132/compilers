package comp0012.target;

public class DynamicVariableFolding {
    public int methodOne() {
        int a = 42;
        int b = (a + 764) * 3;
        a = b - 67;
        return b + 1234 - a;
    }

    public boolean methodTwo() {
        int x = 12345;
        int y = 54321;
        System.out.println(x < y);
        y = 0;
        return x > y;
    }

    public int methodThree() {
        int i = 0;
        int j = i + 3;
        i = j + 4;
        j = i + 5;
        return i * j;
    }
    
    // public int methodFour(){
    //     int a = 1;
    //     int b = 2;

    //     int result = 1;
    //     if (a < b) {
    //         result = a + 3;
    //     } 
    //     // else {
    //     //     result = a + 1;
    //     // }
    //     // a = 4;


    //     if (a > b) {
    //         System.out.println("BOOOOOO");
    //     }
        
    //     b = 2 + result;
    //     return result * b;
    // }

    public int methodFour(){
        int a = 534245;
        int b = a - 1234;
        System.out.println((120298345 - a) * 38.435792873);
        for(int i = 0; i < 10; i++){
            System.out.println((b - a) * i);
        }
        a = 4;
        b = a + 2;
        return a * b;
    }

    public int ifElseBlock() {
        int a = 4;
        int b = 5;
        int result = 0;
        if (a < b) {
            result = b + a;
        } else {
            result = b - a;
        }
        return result;
    }
    
    public int negativeIfElseBlock() {
        int a = 4;
        int b = 5;
        int result = 0;
        if (a > b) {
            result = b + a;
        } else {
            result = b - a;
        }
        return result;
    }
    
    public int ifBlock() {
        int a = 1;
        int b = 2;
        int result = 0;
        if (a < b) {
            result += 3;
        }
        return result;
    }
    
    public int negativeIfBlock() {
        int a = 1;
        int b = 2;
        int result = 0;
        if (a > b) {
            result += 3;
        }
        return result;
    }
    
    public int nestedIfBlock() {
        int a = 1;
        int b = 2;
        int c = 3;
        int d = 4;
        int result = 0;
        if (a < b) {
            if (c > d) {
                result = 1;
            } else {
                result = 2;
            }
        }
        return result;
    }

    public int elseIfBlock() {
        int a = 1;
        int b = 1;
        int result = 0;
        if (a > b) {
            result = 24 + 21;
        } else if (a == b) {
            result = 12 + 11;
        } else {
            result = 6 + 5;
        }
        return result;
    }
    
    public int deadLocalVariables() {
        int a = 32;
        int b = 12;
        System.out.println(b);
        int c = b + 14;
        return c + b;
    }
    
    public int deadLocalVariables2() {
        int a = 32;
        int b = 18 + 2;
        int c = 17 + (a * 2) + b; 
        return b - a;
    }

    public int deadLocalVariables3() {
        int x = 24;
        int y = 142 + 12 + x;
        int z = 13;
        return x;
    }
    
    private int foo2(int x, int y) {
        return (x - 1) * y;
    }
    
    public int deadLocalVariables4() {
        int x = 7;
        int y = 12;
        int result = y - x;
        int z = (x*2) + 12 + foo2(x, y) - y;
        return result;
    }
    
    public int loops() {
        int result = 1;
        int a = 4;
        for (int i = 0; i < 12; i ++ ) {
            a = a + 2;
            result = result + 1;
        }
        return result;
    }

    public int loops2() {
        int a = 3;
        int b = 2;
        int result = 0;
        if (a < b) {
            for (int i = 0; i < 4; i ++) {
                result = i;
            }
        } else {
            result = 16;
        }
        return result;
    }
    
    public int loops3() {
        int a = 3;
        int b = 2;
        int result = 0;
        if (a > b) {
            for (int i = 0; i < 4; i ++) {
                result = i;
            }
        }
        return result;
    }
    
    public int loops4() {
        int a = 3;
        int b = 2;
        int result = 0;
        if (a > b) {
            for (int i = 0; i < 4; i ++) {
                result += i;
            }
        }
        return result;
    }

    public int deadVariableUnary() {
        int a = 0;
        int b = 1;
        int c = -b * -a;
        return a;
    }

    public int negUsed() {
        int a = 3;
        int b = -a;
        return b;
    }

    public int iincUsed() {
        int a = 0;
        a++;
        return a;
    }

}