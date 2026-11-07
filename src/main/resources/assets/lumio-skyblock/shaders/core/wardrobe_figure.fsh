#version 330

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

// A wardrobe figure from the image of all figures, toned by its card. The vertex color carries the tone: red is how much of its color it keeps, green how much of its light, alpha how visible it is.
void main() {
    vec4 color = texture(Sampler0, texCoord);
    if (color.a <= 0.002) {
        discard;
    }
    // the image holds its colors multiplied by alpha
    vec3 plain = color.rgb / color.a;
    float grey = dot(plain, vec3(0.299, 0.587, 0.114));
    fragColor = vec4(mix(vec3(grey), plain, vertexColor.r) * vertexColor.g, color.a * vertexColor.a);
}
