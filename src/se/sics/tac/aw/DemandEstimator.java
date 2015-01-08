package se.sics.tac.aw;

/**
 * Estimate the demand on the flight and hotel auctions using the initial prices of the flight auctions.
 */
public class DemandEstimator {

    private float[] F;
    private float[] H;

    public DemandEstimator(float[] ip, float[] op) {

        float[] s_in;
        float[] s_out;
        float[] f_in;
        float[] f_out;
        int[][] R;

        s_in = calc_s_in();
        s_out = calc_s_out();

        f_in = calc_f_in(s_in, ip);
        f_out = calc_f_out(s_out, op);

        F = calc_F(f_in, f_out);
        R = get_R();

        float[][] F_Matrix = arrayToMatrix(F);
        float[][] H_Matrix = multiply(R, F_Matrix);
        H = matrixToArray(H_Matrix);
    }

    // region Public Methods

    /**
     * Return list of estimated flight demands.
     *
     * @return
     */
    public float[] flightDemands() {

        return F;
    }

    /**
     * Return list of estimated hotel demands.
     *
     * @return
     */
    public float[] hotelDemands() {

        return H;
    }

    // endregion Public Methods

    // region Private Methods

    private float[] calc_s_in() {

        // s_in_i = 0.5 - ((0.1)*i) , i ∈ [1,4]

        float[] _s_in = new float[4];
        for (int i = 0; i < 4; i++) {
            _s_in[i] = (float) 0.5 - (float) (0.1 * (i + 1));
        }
        return _s_in;
    }

    private float[] calc_s_out() {

        // s_out_i = ((0.1)*i) - 0.1 , i ∈ [2,5]

        float[] _s_out = new float[4];
        for (int i = 0; i < 4; i++) {
            _s_out[i] = (float) (0.1 * (i + 2)) - (float) 0.1;
        }
        return _s_out;
    }

    private float[] calc_f_in(float[] s_in, float[] IP) {

        // f_in_i =  s_in_i * (400-IP_i) / 150

        float[] _f_in = new float[4];
        for (int i = 0; i < 4; i++) {
            _f_in[i] = s_in[i] * (400 - IP[i]) / (float) 150;
        }
        return _f_in;
    }

    private float[] calc_f_out(float[] s_out, float[] OP) {

        // f_out_i =  s_out_i * (400-OP_i) / 150

        float[] _f_out = new float[4];
        for (int i = 0; i < 4; i++) {
            _f_out[i] = s_out[i] * (400 - OP[i]) / (float) 150;
        }
        return _f_out;
    }

    private float[] calc_F(float[] f_in, float[] f_out) {

        // F = 64 * ( e_1, e_2, e_3, e_4, m_2, m_3, m_4, m_5);

        // e_i = f_in_i / total(f_in)
        // m_i = f_out_i / total(f_out)

        float total_f_in = sum(f_in);
        float total_f_out = sum(f_out);

        float[] e = new float[4];
        float[] m = new float[4];
        float[] _F = new float[8];

        for (int i = 0; i < 4; i++) {
            e[i] = f_in[i] / total_f_in;
            m[i] = f_out[i] / total_f_out;

            _F[i] = 64 * e[i];
            _F[i + 4] = 64 * m[i];
        }

        return _F;
    }

    private int[][] get_R() {
        int[][] _R = new int[][]
                {
                        {1, 0, 0, 0, 0, 0, 0, 0},
                        {1, 1, 0, 0, -1, 0, 0, 0},
                        {0, 0, 0, -1, 0, 0, 1, 1},
                        {0, 0, 0, 0, 0, 0, 0, 1},
                };
        return _R;
    }

    private float[][] arrayToMatrix(float[] array) {

        int length = array.length;
        float[][] matrix = new float[length][];

        for (int i = 0; i < length; i++) {
            matrix[i] = new float[]{array[i]};
        }
        return matrix;
    }

    private float[] matrixToArray(float[][] matrix) {

        int length = matrix.length;
        float[] array = new float[length];

        for (int i = 0; i < length; i++) {
            array[i] = matrix[i][0];
        }
        return array;
    }

    private float sum(float[] array) {
        float sum = 0;
        for (float i : array) {
            sum += i;
        }
        return sum;
    }

    public static float[][] multiply(int[][] m1, float[][] m2) {

        int m1rows = m1.length;
        int m1cols = m1[0].length;
        int m2rows = m2.length;
        int m2cols = m2[0].length;

        if (m1cols != m2rows) {
            throw new IllegalArgumentException("matrices don't match: " + m1cols + " != " + m2rows);
        }

        float[][] result = new float[m1rows][m2cols];
        for (int i = 0; i < m1rows; i++) { // m1 row
            for (int j = 0; j < m2cols; j++) { // m2 column
                for (int k = 0; k < m1cols; k++) { // m1 column
                    result[i][j] += m1[i][k] * m2[k][j];
                }
            }
        }

        return result;
    }

    // endregion Private Methods
}