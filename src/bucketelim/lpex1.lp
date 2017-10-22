Maximize
 obj: - 5 j + y + 15 k + [ z * j + x * j + y * j - z * j - y * k - z * k ] / 2	
Subject To
 c1: x + y - 100000 j <= 5
 c2: y + z - 100000 k <= 3
Bounds
 0 <= x <= 10
  0 <= y <= 10
   0 <= z <= 10
Binary
 j k
End