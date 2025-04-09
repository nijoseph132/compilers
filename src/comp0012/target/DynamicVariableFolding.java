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

    public int methodFive() {
        int a = 1;
        int b = 2;
        if (a < b) {
            for (int i = 0; i < 3; ++i) System.out.println(b);
        } else {
            for (int i = 0; i < 3; ++i) System.out.println(a);
        }
        return a;
    }

    private int foo(int a, int b) {
        return a + b;
    }

    public int methodSix() {
        int result = 0;

        // // Simple constant comparison that should be removed
        // if (5 < 10) {
        //     result += 1;  // always happens
        // } else {
        //     result -= 100;  // dead branch
        // }
    
        // // Another constant comparison, should remove the if body
        // if (7 > 20) {
        //     result += 500;  // dead branch
        // }
    
        // // This should be folded
        // if (100 == 100) {
        //     result += 2;
        // }
        
        
        // Loop with conditionals inside
        // for (int i = 0; i < 3; i++) {
        //     int x = result + i - (foo(2, 3) * (result + i));
        //     int y = 1;
        //     int a = 1 + 2;
        //     int b = 2 + a;
        //     if (a < b) {
        //         result += i;
        //     } else {
        //         result -= i;  // dead branch
        //     }
        // }
    
        // // Branch with else that can be eliminated
        // if (0 != 0) {
        //     result = -999;  // dead
        // } else {
        //     result += 3;
        // }
    
        // // LCMP-like pattern with long constants
        // long a = 50L;
        // long b = 25L;
        // if (a > b) {
        //     result += 4;
        // } else {
        //     result -= 4;  // dead
        // }
    
        // // More comparisons for fun
        // if (8 <= 8) {
        //     result *= 2;
        // }
    
        // if (9 >= 10) {
        //     result /= 2;  // dead
        // }
    
        // // Redundant nested ifs
        int a = 4;
        int b = 5;
        if (a < b) {
            if (a > b) {
                result += 10;
            } else {
                result += 1;
            }
        } else {
            result += 100;
        }
    
        return result;
    }
}