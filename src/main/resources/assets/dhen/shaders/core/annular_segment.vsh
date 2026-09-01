#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec4 Color;

out vec2 localPosition;
flat out vec2 radii;
flat out vec2 angles;
out vec4 vertexColor;

const float SUBPIXEL = 8.0;
const float ANGLE_SUBPIXEL = 4096.0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    localPosition = UV0;
    radii = vec2(UV1) / SUBPIXEL;
    angles = vec2(UV2) / ANGLE_SUBPIXEL;
    vertexColor = Color;
}
