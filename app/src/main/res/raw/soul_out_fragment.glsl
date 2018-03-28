precision mediump float;
varying vec2 textureCoordinate;
uniform sampler2D s_texture;
uniform float percentage;
void main()
{
    float alpha = 0.3 * (1.0 - percentage);
    float divider = 1.0 - percentage;
    float offset = percentage / 2.0;

    vec2 scaleVector;
    scaleVector[0]=textureCoordinate.x * divider +offset;
    scaleVector[1]=textureCoordinate.y * divider +offset;

    gl_FragColor = texture2D( s_texture, textureCoordinate)*(1.0 - alpha)+ texture2D( s_texture, scaleVector)*alpha;
}