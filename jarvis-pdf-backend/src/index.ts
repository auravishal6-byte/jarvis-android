import { PDFDocument, rgb, type PDFFont, type PDFPage } from "pdf-lib";
import fontkit from "@pdf-lib/fontkit";
import devanagariFont from "./fonts/NotoSansDevanagari-Regular.ttf";

interface Env {
  GEMINI_API_KEY: string;
}

interface PDFSection {
  heading: string;
  paragraphs: string[];
  bullets: string[];
}

interface AIResult {
  title: string;
  subtitle: string;
  sections: PDFSection[];
}

const MODEL = "gemini-3.5-flash-lite";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function safeFilename(value: string): string {
  return value
    .replace(/[^\p{L}\p{N}\-_ ]/gu, "")
    .trim()
    .replace(/\s+/g, "_")
    .slice(0, 60) || "JARVIS_Document";
}

async function generateContent(
  prompt: string,
  language: string,
  apiKey: string,
): Promise<AIResult> {
  const instruction = `
Create a professional, detailed document based on this request:
${prompt}

Write the document in ${language === "hi" ? "Hindi" : "English"}.

Return ONLY valid JSON in this exact structure:
{
  "title": "Document title",
  "subtitle": "Short description",
  "sections": [
    {
      "heading": "Section heading",
      "paragraphs": ["Paragraph one", "Paragraph two"],
      "bullets": ["Point one", "Point two"]
    }
  ]
}

Use useful headings, factual and clear content, and appropriate bullet points.
Do not include markdown fences. Do not invent facts or citations.
`;

  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": apiKey,
      },
      body: JSON.stringify({
        contents: [{ parts: [{ text: instruction }] }],
        generationConfig: {
          responseMimeType: "application/json",
          temperature: 0.5,
          maxOutputTokens: 7000,
        },
      }),
    },
  );

  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`Gemini API error (${response.status}): ${detail.slice(0, 500)}`);
  }

  const result = await response.json() as {
    candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }>;
  };

  const output = result.candidates?.[0]?.content?.parts
    ?.map(part => part.text || "")
    .join("");

  if (!output) throw new Error("Gemini returned empty content.");

  const parsed = JSON.parse(output) as AIResult;

  if (
    typeof parsed.title !== "string" ||
    !Array.isArray(parsed.sections) ||
    parsed.sections.length === 0
  ) {
    throw new Error("Gemini returned an invalid document structure.");
  }

  return parsed;
}

function wrapText(
  text: string,
  font: PDFFont,
  size: number,
  maxWidth: number,
): string[] {
  const words = text.split(/\s+/);
  const lines: string[] = [];
  let line = "";

  for (const word of words) {
    const candidate = line ? `${line} ${word}` : word;

    if (font.widthOfTextAtSize(candidate, size) <= maxWidth) {
      line = candidate;
      continue;
    }

    if (line) lines.push(line);

    if (font.widthOfTextAtSize(word, size) <= maxWidth) {
      line = word;
    } else {
      let part = "";
      for (const char of word) {
        const next = part + char;
        if (font.widthOfTextAtSize(next, size) > maxWidth && part) {
          lines.push(part);
          part = char;
        } else {
          part = next;
        }
      }
      line = part;
    }
  }

  if (line) lines.push(line);
  return lines;
}

async function createPDF(data: AIResult): Promise<Uint8Array> {
  const pdf = await PDFDocument.create();
  pdf.registerFontkit(fontkit);

  const font = await pdf.embedFont(devanagariFont, { subset: true });

  pdf.setTitle(data.title);
  pdf.setAuthor("JARVIS AI Assistant");
  pdf.setSubject(data.subtitle || "AI generated document");
  pdf.setCreator("JARVIS Professional PDF Maker");

  const width = 595.28;
  const height = 841.89;
  const margin = 48;
  const contentWidth = width - margin * 2;

  const navy = rgb(0.08, 0.13, 0.24);
  const blue = rgb(0.12, 0.36, 0.70);
  const dark = rgb(0.16, 0.19, 0.24);
  const grey = rgb(0.42, 0.46, 0.52);
  const light = rgb(0.91, 0.94, 0.98);

  let page!: PDFPage;
  let y = 0;
  let pageCount = 0;

  function newPage(): void {
    page = pdf.addPage([width, height]);
    pageCount++;
    y = height - margin;
  }

  function ensureSpace(required: number): void {
    if (y - required < margin + 24) newPage();
  }

  function drawParagraph(text: string, size = 10.5): void {
    const lines = wrapText(text, font, size, contentWidth);

    for (const line of lines) {
      ensureSpace(size + 7);
      page.drawText(line, {
        x: margin,
        y: y - size,
        size,
        font,
        color: dark,
      });
      y -= size + 7;
    }
    y -= 5;
  }

  function drawSectionHeading(text: string): void {
    ensureSpace(42);
    y -= 8;
    page.drawRectangle({
      x: margin,
      y: y - 9,
      width: 4,
      height: 22,
      color: blue,
    });
    page.drawText(text, {
      x: margin + 12,
      y: y - 5,
      size: 13,
      font,
      color: navy,
    });
    y -= 32;
  }

  newPage();

  page.drawRectangle({
    x: 0,
    y: height - 190,
    width,
    height: 190,
    color: navy,
  });

  page.drawText("JARVIS  |  PROFESSIONAL DOCUMENT", {
    x: margin,
    y: height - 55,
    size: 9,
    font,
    color: rgb(0.72, 0.83, 0.98),
  });

  const titleLines = wrapText(data.title, font, 23, contentWidth);

  let titleY = height - 100;
  for (const line of titleLines.slice(0, 3)) {
    page.drawText(line, {
      x: margin,
      y: titleY,
      size: 23,
      font,
      color: rgb(1, 1, 1),
    });
    titleY -= 31;
  }

  if (data.subtitle) {
    page.drawText(
      wrapText(data.subtitle, font, 10, contentWidth).slice(0, 2)[0] || "",
      {
        x: margin,
        y: height - 165,
        size: 10,
        font,
        color: rgb(0.82, 0.87, 0.95),
      },
    );
  }

  y = height - 225;

  for (const section of data.sections.slice(0, 30)) {
    if (section.heading) drawSectionHeading(section.heading);

    for (const paragraph of (section.paragraphs || []).slice(0, 20)) {
      if (typeof paragraph === "string") drawParagraph(paragraph);
    }

    for (const bullet of (section.bullets || []).slice(0, 30)) {
      if (typeof bullet !== "string") continue;

      const bulletLines = wrapText(bullet, font, 10.5, contentWidth - 18);

      for (let i = 0; i < bulletLines.length; i++) {
        ensureSpace(20);
        if (i === 0) {
          page.drawText("•", {
            x: margin + 2,
            y: y - 10,
            size: 11,
            font,
            color: blue,
          });
        }
        page.drawText(bulletLines[i], {
          x: margin + 18,
          y: y - 10,
          size: 10.5,
          font,
          color: dark,
        });
        y -= 17;
      }
      y -= 4;
    }
  }

  const pages = pdf.getPages();

  pages.forEach((currentPage, index) => {
    currentPage.drawLine({
      start: { x: margin, y: 35 },
      end: { x: width - margin, y: 35 },
      thickness: 0.6,
      color: light,
    });

    currentPage.drawText("Generated by JARVIS AI Assistant", {
      x: margin,
      y: 21,
      size: 8,
      font,
      color: grey,
    });

    currentPage.drawText(`Page ${index + 1} of ${pages.length}`, {
      x: width - margin - 75,
      y: 21,
      size: 8,
      font,
      color: grey,
    });
  });

  return pdf.save();
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders });
    }

    if (request.method === "GET") {
      return json({
        service: "JARVIS Professional PDF Maker",
        status: "online",
      });
    }

    if (request.method !== "POST") {
      return json({ error: "Method not allowed." }, 405);
    }

    if (!env.GEMINI_API_KEY) {
      return json({ error: "Backend API key is not configured." }, 500);
    }

    try {
      const contentLength = Number(request.headers.get("Content-Length") || 0);
      if (contentLength > 15000) {
        return json({ error: "Request is too large." }, 413);
      }

      const body = await request.json() as {
        prompt?: string;
        title?: string;
        language?: string;
      };

      const prompt = body.prompt?.trim();

      if (!prompt || prompt.length > 12000) {
        return json({ error: "Please provide a prompt (maximum 12,000 characters)." }, 400);
      }

      const language = body.language === "hi" ? "hi" : "en";

      const aiDocument = await generateContent(
        prompt,
        language,
        env.GEMINI_API_KEY,
      );

      if (body.title?.trim()) {
        aiDocument.title = body.title.trim().slice(0, 150);
      }

      const pdfBytes = await createPDF(aiDocument);
      const filename = safeFilename(aiDocument.title) + ".pdf";

      return new Response(pdfBytes as BodyInit, {
        status: 200,
        headers: {
          ...corsHeaders,
          "Content-Type": "application/pdf",
          "Content-Disposition": `attachment; filename="${filename}"`,
          "Cache-Control": "no-store",
        },
      });
    } catch (error) {
      console.error("PDF generation failed:", error);
      return json({
        error: error instanceof Error
          ? error.message.slice(0, 500)
          : "PDF generation failed.",
      }, 500);
    }
  },
} satisfies ExportedHandler<Env>;
